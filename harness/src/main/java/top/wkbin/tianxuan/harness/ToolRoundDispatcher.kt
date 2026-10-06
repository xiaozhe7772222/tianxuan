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
    /** 工作区（或语义等价的 scope key）→ 互斥锁；blank key 兜底为全局单锁。 */
    private val mutationMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(scopeKey: String): Mutex =
        mutationMutexes.getOrPut(scopeKey.trim().ifBlank { GLOBAL_SCOPE }) { Mutex() }

    /**
     * 按工作区串行执行变更类副作用。除本调度器外，审批恢复路径（被批准的
     * write/base/mcp 等）也必须经此方法取锁，否则会与并发会话的同工作区写入踩踏。
     */
    suspend fun <T> withMutationLock(scopeKey: String, block: suspend () -> T): T =
        mutexFor(scopeKey).withLock { block() }

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
        val mutex = mutexFor(mutationScope)
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
    }

    companion object {
        const val DEFAULT_PARALLELISM = 4
        private const val GLOBAL_SCOPE = "<global>"
    }
}
