package top.wkbin.tianxuan.harness.projection

import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.UserMessage

/**
 * 会话统计投影（对齐 DSH session-stats/src/projection.ts）。
 *
 * 纯折叠：从 step 边界、流内嵌流、工具配对和组装的助手消息
 * 推导出全日志计数和墙时。
 *
 * 计数依赖消息类型而非 step 事件（天玄没有 step/start | step/end 事件），
 * 用 AssistantText 交替计步（每个 AssistantText = 一次模型调用 = 一步）。
 *
 * 工具时间配对 tool_call → tool_result（按 toolCallId）。
 */
object SessionStatsProjection : ProjectionDefinition<String, SessionStatsProjection.StatsState> {

    override val key: String = "sessionStats"

    /** 全日志累计数字。 */
    data class StatsTotals(
        /** 不同的用户轮次数（UserMessage 数）。 */
        val turns: Int = 0,
        /** 步数（AssistantText 数）。 */
        val steps: Int = 0,
        /** 工具调用次数（ToolCall 数）。 */
        val toolCalls: Int = 0,
        /** 工具成功次数。 */
        val toolSuccesses: Int = 0,
        /** 工具失败次数。 */
        val toolFailures: Int = 0,
        /** 估算总 token（用消息字节数粗估）。 */
        val estimatedTokens: Int = 0,
    )

    /** 折叠状态：总计 + 进行中的边界。 */
    data class StatsState(
        val totals: StatsTotals = StatsTotals(),
        /** 当前未匹配结果的 tool_call id 集合（待配对）。 */
        val pendingToolCalls: Set<String> = emptySet(),
    )

    override fun init(header: ProjectionSessionMeta, inheritedEventCount: Int): StatsState = StatsState()

    override fun apply(state: StatsState, event: HarnessMessage): StatsState {
        val totals = state.totals
        return when (event) {
            is UserMessage -> state.copy(
                totals = totals.copy(
                    turns = totals.turns + 1,
                    estimatedTokens = totals.estimatedTokens + estimateTokens(event.text),
                ),
            )
            is AssistantText -> state.copy(
                totals = totals.copy(
                    steps = totals.steps + 1,
                    estimatedTokens = totals.estimatedTokens + estimateTokens(event.text),
                ),
            )
            is ToolCall -> state.copy(
                totals = totals.copy(
                    toolCalls = totals.toolCalls + 1,
                    estimatedTokens = totals.estimatedTokens + estimateTokens(event.args.toString()),
                ),
                pendingToolCalls = state.pendingToolCalls + event.id,
            )
            is ToolResult -> {
                val newSuccesses = if (event.success) totals.toolSuccesses + 1 else totals.toolSuccesses
                val newFailures = if (!event.success) totals.toolFailures + 1 else totals.toolFailures
                state.copy(
                    totals = totals.copy(
                        toolSuccesses = newSuccesses,
                        toolFailures = newFailures,
                        estimatedTokens = totals.estimatedTokens + estimateTokens(event.output),
                    ),
                    pendingToolCalls = state.pendingToolCalls - event.toolCallId,
                )
            }
            else -> state // 不关心的消息类型：返回同一引用
        }
    }

    override fun view(state: StatsState): Any? = state.totals

    private fun estimateTokens(text: String): Int = text.length / 4
}
