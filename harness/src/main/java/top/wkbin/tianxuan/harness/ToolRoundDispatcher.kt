package top.wkbin.tianxuan.harness

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 单回合多工具调用的受限并发调度器。
 *
 * 约束：
 * - [isParallelSafe] 为真的工具（只读工具，以及自行协调写隔离的编排型工具）在 [parallelism]
 *   个许可内并发执行，不参与全局变更互斥；
 * - 变更类工具（写文件/命令/下载/MCP 等）按 mutationScope（工作区）互斥，避免同一工作区
 *   内的副作用互相踩踏。互斥只按工作区分片：BASE 超时上限 1 小时、DOWNLOAD 可达 4GB×10
 *   次重试，若做成跨会话全局单例，一个工作区的长构建会挡住所有其他工作区的普通写入；
 *   审批恢复（resolveApproval）执行被批准的变更工具也经 [withMutationLock] 走同一把锁；
 * - 任一工具触发审批暂停（[Pause.abort]）后，尚未开始的工具不再启动，在途工具自然跑完，
 *   与原串行"中途暂停、后续调用不执行"的语义保持一致；
 * - 取消沿结构化并发传播：外层 Job 被取消时，所有在途工具被打断并向上抛出
 *   CancellationException，由 HarnessLoop 的悬空调用修复逻辑收尾。
 */
class ToolRoundDispatcher() {
    /**
     * 工作区（或语义等价的 scope key）→ 互斥锁；blank key 兜底为全局单锁。
     *
     * 键必须回收。调度器是 Koin single（进程级），而 scopeKey 是工作区路径——
     * 用户每开一个新工作区就往这里塞一把永不移除的锁，长时间使用的设备上
     * 这张表只增不减，是一处按工作区数量线性增长的泄漏。
     *
     * 回收策略见 [acquireLock] / [releaseLock] 的引用计数，不能用 WeakReference：
     * Mutex 与持有它的协程互相引用，只要还有在途等待者就不会被回收，而等待者
     * 可能长期挂在 BASE 的 1 小时超时上。
     *
     * **本表的一切读写都必须持有 [registrationLock]。** 表本身是普通
     * `HashMap` 而非 `ConcurrentHashMap`，这是刻意的：
     *
     * 引用计数回收的死结在于「计数归零判定」与「移除表项」必须原子，否则
     * 中间那段窗口里入场的持有者会拿到一把**已被删除**的锁，而随后的请求
     * 会新建第二把——同一工作区被两把 Mutex 保护，并发保护静默失效。
     * 早先的实现用 `ConcurrentHashMap.compute` 做「建锁 + 计数 +1」，靠
     * `compute` 的分段锁保证那一步原子，再用 `remove(key, slot)` 的
     * compare-and-remove 兜底删除；但这**掩盖不了**窗口依然存在：
     * `compute` 与 `remove` 是两次独立的分段锁操作，中间可以插入别人的
     * `compute`。补一个「事后撤销」也救不回来——被删的槽位计数已经不为零，
     * 撤销与删除之间又有新的窗口，只会把竞态推给下一轮。
     *
     * 既然两把分段锁无法拼成一个原子区间，就换成**一把显式的对象锁**
     * 覆盖整段读改写。代价是跨工作区的取锁/放锁会短暂串行，但那只是几次
     * 哈希表操作（纳秒级），真正的互斥等待仍然发生在各自的 [LockSlot.mutex]
     * 上，不会让一个工作区的长构建挡住其他工作区——这正是分片互斥要保住的性质。
     */
    private val mutationMutexes = HashMap<String, LockSlot>()

    /** 保护 [mutationMutexes] 的注册表锁；只覆盖建表/计数/回收，不覆盖临界区。 */
    private val registrationLock = Any()

    /**
     * 取一把作用域锁，并登记一次持有。
     *
     * 必须在 finally 里配对 [releaseLock]，否则计数永不归零、锁永不回收。
     * 这与「tryLock 后忘记 unlock」是同一类错误，只是发生在更隐蔽的地方。
     *
     * 「建锁」与「计数 +1」在 [registrationLock] 下一起完成。若分两步
     * （先 getOrPut 再 increment），两个并发首次进入同一工作区的请求会各自
     * 拿到 1，而实际有两个持有者——其中一个退出时就把锁删了，另一个仍在等锁，
     * 于是并发保护静默失效。
     */
    private fun acquireLock(scopeKey: String): LockSlot {
        val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
        return synchronized(registrationLock) {
            val existing = mutationMutexes[key]
            if (existing != null) {
                existing.holders++
                existing
            } else {
                LockSlot(holders = 1).also { mutationMutexes[key] = it }
            }
        }
    }

    /**
     * 释放一次持有；计数归零时从 [mutationMutexes] 移除该键，让作用域条目不再常驻。
     *
     * 「计数减一」与「归零则移除」在**同一个** [registrationLock] 区间内完成，
     * 因此不存在「判定归零之后、执行移除之前」的窗口——任何并发入场者要么
     * 在这段区间之前把计数加上去（于是归零不成立，不移除），要么在这段区间
     * 之后看到键已消失并新建一把锁（而前一名持有者此刻已经彻底离场）。
     * 两种次序都只有一个持有者集合，不会出现同一工作区两把锁。
     *
     * 表项的生命周期因此严格是「计数 0 ⇔ 不在表内」：不持有 [LockSlot.mutex]
     * 的协程绝不会在表里留下计数不为零的孤儿槽位，也就无需任何事后撤销。
     */
    private fun releaseLock(scopeKey: String, slot: LockSlot) {
        val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
        synchronized(registrationLock) {
            slot.holders--
            if (slot.holders <= 0 && mutationMutexes[key] === slot) {
                mutationMutexes.remove(key)
            }
        }
    }

    /**
     * 一个作用域锁及其在途持有者计数。
     *
     * [holders] 只在 [registrationLock] 下读写，故无需 volatile；
     * 声明为普通字段是为了让「必须持锁访问」这一约束在编译期无可乘之机
     * （volatile 会给人「随便读也安全」的错觉）。
     */
    private class LockSlot(
        var holders: Int,
    ) {
        val mutex = Mutex()
    }

    /**
     * 当前常驻的作用域锁数量。仅供测试与诊断观察回收是否生效。
     *
     * 暴露它而不是让测试去反射私有字段：反射拿到的是实现细节，字段一改名
     * 测试就红，而这里本来就是一个稳定可观测的运行指标。
     */
    fun activeScopeCount(): Int = synchronized(registrationLock) { mutationMutexes.size }

    /**
     * 按工作区串行执行变更类副作用。除本调度器外，审批恢复路径（被批准的
     * write/base/mcp 等）也必须经此方法取锁，否则会与并发会话的同工作区写入踩踏。
     *
     * [acquireLock] 返回的 slot 在本方法返回前始终在表内：只要计数不为零，
     * 回收就不可能发生（见 [releaseLock]）。因此这里可以放心直接用它的 mutex，
     * 不必再做事后确认。
     */
    suspend fun <T> withMutationLock(scopeKey: String, block: suspend () -> T): T {
        val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
        val slot = acquireLock(key)
        try {
            return slot.mutex.withLock { block() }
        } finally {
            releaseLock(key, slot)
        }
    }

    class Pause private constructor() {
        private val aborted = AtomicBoolean(false)
        fun abort() { aborted.set(true) }
        fun isAborted(): Boolean = aborted.get()

        companion object {
            fun create(): Pause = Pause()
        }
    }

    suspend fun <T> dispatch(
        items: List<T>,
        parallelism: Int = DEFAULT_PARALLELISM,
        mutationScope: String = "",
        isParallelSafe: (T) -> Boolean,
        run: suspend (T, Pause) -> Unit,
    ) {
        if (items.isEmpty()) return
        val key = mutationScope.trim().ifBlank { GLOBAL_SCOPE }
        val slot = acquireLock(key)
        try {
            val mutex = slot.mutex
            if (items.size == 1 || parallelism <= 1) {
                val pause = Pause.create()
                items.forEach { item ->
                    if (pause.isAborted()) return
                    if (isParallelSafe(item)) run(item, pause)
                    else mutex.withLock {
                        if (!pause.isAborted()) run(item, pause)
                    }
                }
                return
            }
            val pause = Pause.create()
            val permits = Semaphore(parallelism)
            coroutineScope {
                items.forEach { item ->
                    launch {
                        permits.withPermit {
                            if (pause.isAborted()) return@withPermit
                            if (isParallelSafe(item)) run(item, pause)
                            else mutex.withLock {
                                if (!pause.isAborted()) run(item, pause)
                            }
                        }
                    }
                }
            }
        } finally {
            releaseLock(key, slot)
        }
    }

    companion object {
        const val DEFAULT_PARALLELISM = 4
        private const val GLOBAL_SCOPE = "<global>"
    }
}
