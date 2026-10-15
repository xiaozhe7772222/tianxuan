package top.wkbin.tianxuan.harness.ptc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.harness.events.AgentEventLogger

/**
 * PTC 子调度日志写入（对齐 DSH core/tools/src/ptc.ts 的 dispatch log）。
 *
 * 每个 PTC 子调用在完成时写入一条结构化日志到会话事件流，
 * 使恢复、压缩与回溯可精确区分哪些子调用成功、哪些失败、各自耗时。
 *
 * 日志格式对齐 DSH 的 `tool/ptc-dispatch` 事件：
 * - callId: 子调用的唯一标识（rootCallId:ptc:N）
 * - name: 工具名
 * - arguments: 调用的 JSON 参数（已脱敏快照）
 * - isError: 是否失败
 * - durationMs: 实际执行耗时
 */
class PtcDispatchLogger(
    private val eventLogger: AgentEventLogger,
) {
    /**
     * 记录一个子调用的完成。
     *
     * @param sessionId 会话 ID
     * @param rootCallId 根调用的 ID（外层 run_code 的 callId）
     * @param subCallIndex 子调用的索引（0-based）
     * @param toolName 工具名
     * @param args 调用参数（JSON 字符串）
     * @param success 是否成功
     * @param output 输出摘要（截断到 200 字符）
     * @param durationMs 执行耗时（毫秒）
     */
    suspend fun logDispatch(
        sessionId: String,
        rootCallId: String,
        subCallIndex: Int,
        toolName: String,
        args: String,
        success: Boolean,
        output: String,
        durationMs: Long,
    ) {
        val subCallId = "$rootCallId:ptc:$subCallIndex"
        val status = if (success) "success" else "failed"
        val summary = output.take(200).replace("\n", " ")
        eventLogger.log(
            sessionId = sessionId,
            tag = "ptc-dispatch",
            message = "subCallId=$subCallId tool=$toolName status=$status duration=${durationMs}ms output=$summary",
        )
    }

    /**
     * 记录一个子调用的开始。
     */
    suspend fun logDispatchStart(
        sessionId: String,
        rootCallId: String,
        subCallIndex: Int,
        toolName: String,
    ) {
        val subCallId = "$rootCallId:ptc:$subCallIndex"
        eventLogger.log(
            sessionId = sessionId,
            tag = "ptc-dispatch-start",
            message = "subCallId=$subCallId tool=$toolName",
        )
    }
}
