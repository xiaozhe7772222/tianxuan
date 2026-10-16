package top.wkbin.tianxuan.harness.ptc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * PTC 并发子调用调度器（对齐 DSH core/tools/src/ptc.ts 的 dispatch 管道）。
 *
 * 核心机制：单驱动车道 + 有序提交光标：
 * - 每个子调用按提交序进入队列
 * - 分类决定 parallel/exclusive：
 *   - PARALLEL：并行运行，最多 [maxParallel] 个并发
 *   - EXCLUSIVE：等待池排空后独占运行；运行期间后续调用不启动
 * - 结果按提交序写入，保持 head-of-line 语义
 *
 * 中止语义：cancel() 取消所有排队与在飞调用；drain() 等待在飞调用静默。
 */
class PtcDispatcher(
    private val maxParallel: Int = 4,
) {
    enum class ExecutionMode { PARALLEL, EXCLUSIVE }

    private data class PendingDispatch(
        val mode: ExecutionMode,
        val execute: suspend () -> Unit,
    )

    private val stateMutex = Mutex()
    private val pendingQueue = mutableListOf<PendingDispatch>()
    private var exclusiveActive = false
    private var cancelled = false

    /** run 已结束（cancel/drain 后）。 */
    val isOver: Boolean get() = cancelled

    /**
     * 提交一个子调用执行。
     *
     * @param mode 执行模式
     * @param execute 实际执行体
     */
    suspend fun submit(mode: ExecutionMode = ExecutionMode.PARALLEL, execute: suspend () -> Unit) {
        if (cancelled) return
        stateMutex.withLock { pendingQueue.add(PendingDispatch(mode, execute)) }
    }

    /**
     * 排空所有在飞 dispatch（run settle 时调用）。
     * 未启动的排队调用被放弃；已启动的等待其静默。
     */
    suspend fun drain() {
        cancelled = true
        stateMutex.withLock { pendingQueue.clear() }
        // 让驱动循环在下一轮检测到 cancelled 后退出
        delay(10)
    }

    /** 取消所有在飞与排队调用。 */
    suspend fun cancel() {
        cancelled = true
        stateMutex.withLock { pendingQueue.clear() }
    }

    /**
     * 并发执行所有排队调用（保持提交序）。
     *
     * 由调用方在全部 submit 完成后调用一次。
     * 每个执行体按 [maxParallel] 并行度执行；EXCLUSIVE 调用独占。
     *
     * @return 每个调用（按提交序）的执行结果；失败也返回而非抛出
     */
    suspend fun runAll(scope: CoroutineScope): List<Result<Unit>> {
        // 快照当前队列（后续 submit 不再进入此批次）
        val batch = stateMutex.withLock {
            pendingQueue.toList().also { pendingQueue.clear() }
        }
        if (cancelled) {
            return batch.map { Result.failure(IllegalStateException("run is over; dispatch abandoned")) }
        }

        // 分段调度（对齐类注释语义）：连续 PARALLEL 归一段，用 [Semaphore] 按 [maxParallel]
        // 限流并发；EXCLUSIVE 自成一段且在段内独占执行。段与段串行，因此独占运行期间
        // 前后调用都不会启动，结果仍按提交序写回。
        val results = arrayOfNulls<Result<Unit>>(batch.size)
        var index = 0
        while (index < batch.size) {
            if (batch[index].mode == ExecutionMode.EXCLUSIVE) {
                results[index] = runCatching { batch[index].execute() }
                index += 1
                continue
            }
            val end = (index until batch.size)
                .firstOrNull { batch[it].mode == ExecutionMode.EXCLUSIVE } ?: batch.size
            val permits = Semaphore(maxParallel.coerceAtLeast(1))
            val jobs = (index until end).map { i ->
                scope.async { permits.withPermit { results[i] = runCatching { batch[i].execute() } } }
            }
            jobs.forEach { it.await() }
            index = end
        }
        return results.toList().map { it ?: Result.success(Unit) }
    }
}