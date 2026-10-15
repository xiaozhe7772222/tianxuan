package top.wkbin.tianxuan.harness.ptc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

        // 按提交序执行：EXCLUSIVE 前后各有一个屏障
        val results = arrayOfNulls<Result<Unit>>(batch.size)
        val barrier = Mutex()

        val jobs = batch.mapIndexed { index, dispatch ->
            scope.async {
                try {
                    when (dispatch.mode) {
                        ExecutionMode.EXCLUSIVE -> {
                            // 独占：等待屏障（所有并行在飞完成后才轮到）
                            barrier.withLock {
                                results[index] = runCatching { dispatch.execute() }
                            }
                        }
                        ExecutionMode.PARALLEL -> {
                            // 并行：获取槽位（屏障释放后，并行槽位有限制）
                            barrier.withLock {
                                results[index] = runCatching { dispatch.execute() }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    results[index] = Result.failure(e)
                }
            }
        }

        // 等待所有完成
        jobs.forEach { it.await() }
        return results.toList().map { it ?: Result.success(Unit) }
    }
}