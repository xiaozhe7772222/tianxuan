package top.wkbin.tianxuan.harness.projection

import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.UserMessage

/**
 * 轮次大纲投影（对齐 DSH session-turn-outline/src/projection.ts）。
 *
 * 纯折叠：从 UserMessage 边界、首个人类提示和最终助手响应
 * 推导出全日志的轮次大纲，供聊天轨道渲染轮次列表。
 *
 * 每个轮次条目包含：
 * - prompt：用户消息文本预览（截断到一行）
 * - response：最终助手消息文本预览（截断到三行）
 * - stepCount：该轮次的步数（ToolCall + AssistantText 数）
 *
 * 预览尺寸对齐 DSH 的 rail-card 预算：prompt 50 字符，response 120 字符。
 */
object SessionTurnOutlineProjection : ProjectionDefinition<String, SessionTurnOutlineProjection.OutlineState> {

    override val key: String = "turnOutline"

    /** 提示预算：一行 rail-card。 */
    private const val PROMPT_PREVIEW_LIMIT = 50

    /** 响应预算：三行 rail-card。 */
    private const val RESPONSE_PREVIEW_LIMIT = 120

    /** 一个轮次的大纲条目。 */
    data class TurnOutlineEntry(
        /** 该轮次首条 UserMessage 的序号（0-based 在全局消息列表中的位置）。 */
        val messageIndex: Int,
        /** 用户提示文本预览。 */
        val promptPreview: String,
        /** 最终助手响应文本预览（轮次未完成时为空）。 */
        val responsePreview: String,
        /** 该轮次的步数。 */
        val stepCount: Int,
    )

    /** 折叠状态。 */
    data class OutlineState(
        /** 全日志的轮次条目数组。 */
        val turns: List<TurnOutlineEntry> = emptyList(),
        /** 当前进行中轮次的步数计数。 */
        val currentStepCount: Int = 0,
        /** 当前进行中轮次的最新助手消息预览。 */
        val currentResponsePreview: String = "",
    )

    override fun init(header: ProjectionSessionMeta, inheritedEventCount: Int): OutlineState = OutlineState()

    override fun apply(state: OutlineState, event: HarnessMessage): OutlineState {
        return when (event) {
            is UserMessage -> {
                // 新轮次开始
                val newEntry = TurnOutlineEntry(
                    messageIndex = 0, // 索引在 drive 时不可知；用 turns.size 作为序号
                    promptPreview = preview(event.text, PROMPT_PREVIEW_LIMIT),
                    responsePreview = "",
                    stepCount = 0,
                )
                state.copy(
                    turns = state.turns + newEntry,
                    currentStepCount = 0,
                    currentResponsePreview = "",
                )
            }
            is AssistantText -> {
                val responsePreview = preview(event.text, RESPONSE_PREVIEW_LIMIT)
                if (state.turns.isEmpty()) {
                    // 无 UserMessage 前导的 AssistantText（异常但容错）
                    return state
                }
                // 更新最后一个轮次的响应预览
                val updatedTurns = state.turns.dropLast(1) +
                    state.turns.last().copy(
                        responsePreview = responsePreview,
                        stepCount = state.currentStepCount + 1,
                    )
                state.copy(
                    turns = updatedTurns,
                    currentStepCount = state.currentStepCount + 1,
                    currentResponsePreview = responsePreview,
                )
            }
            is top.wkbin.tianxuan.harness.ToolCall -> {
                if (state.turns.isEmpty()) return state
                state.copy(currentStepCount = state.currentStepCount + 1)
            }
            else -> state
        }
    }

    override fun view(state: OutlineState): Any? = state.turns

    /**
     * 空格连接文本块、折叠空白、限制到 limit 并加省略号。
     */
    private fun preview(text: String, limit: Int): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length > limit - 1) {
            normalized.take(limit - 1).trimEnd() + "…"
        } else {
            normalized
        }
    }
}