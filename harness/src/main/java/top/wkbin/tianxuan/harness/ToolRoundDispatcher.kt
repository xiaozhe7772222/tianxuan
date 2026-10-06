package top.wkbin.tianxuan.harness

import java.util.concurrent.ConcurrentHashMap
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
     * 回收策略见 [acquireLock] / [releaseLock]的引用计数，不能用 WeakReference：
     * Mutex 与持有它的协程互相引用，只要还有在途等待者就不会被回收，而等待者
     * 可能长期挂在 BASE 的 1 小时超时上。
     */
    private val mutationMutexes = ConcurrentHashMap<String, LockSlot>()

    /**
     * 取一把作用域锁，并登记一次持有。
     *
     * 必须在 finally 里配对 [releaseLock]，否则计数永不归零、锁永不回收。
     * 这与「tryLock 后忘记 unlock」是同一类错误，只是发生在更隐蔽的地方。
     */
    private fun acquireLock(scopeKey: String): LockSlot {
        val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
        // compute 保证「建锁」与「计数 +1」原子完成。若分两步（先 getOrPut 再
        // increment），两个并发首次进入同一工作区的请求会各自拿到 1，
        // 而实际有两个持有者——其中一个退出时就把锁删了，另一个仍在等锁，
        // 于是并发保护静默失效。
        return mutationMutexes.compute(key) { _, existing ->
            (existing ?: LockSlot()).also { it.holders++ }
        }!!
    }

    /** 释放一次持有；计数归零时移除锁，让该作用域的条目不再常驻。 */
    private fun releaseLock(scopeKey: String, slot: LockSlot) {
        val last = synchronized(slot) {
            slot.holders--
            slot.holders <= 0
        }
        if (!last) return
        // 只能移除「key 仍指向本slot」的那一条。若期间该key 已被移除又新建
        // （新持有者已入场），误删别人的锁会让两个持有者共用一个 Mutex——
        // 那比不回收严重得多：并发保护会静默失效。
        mutationMutexes.remove(scopeKey, slot)
    }

    /** 一个作用域锁及其在途持有者计数。计数会在锁外被读，故用 volatile。 */
    private class LockSlot {
        val mutex = Mutex()

        @Volatile
        var holders: Int = 0
    }

    /**
     * 当前常驻的作用域锁数量。仅供测试与诊断观察回收是否生效。
     *
     * 暴露它而不是让测试去反射私有字段：反射拿到的是实现细节，字段一改名
     * 测试就红，而这里本来就是一个稳定可观测的运行指标。
     */
    fun activeScopeCount(): Int = mutationMutexes.size

    /**
     * 按工作区串行执行变更类副作用。除本调度器外，审批恢复路径（被批准的
     * write/base/mcp 等）也必须经此方法取锁，否则会与并发会话的同工作区写入踩踏。
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
