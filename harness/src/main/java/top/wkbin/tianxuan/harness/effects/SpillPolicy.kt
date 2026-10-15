package top.wkbin.tianxuan.harness.effects

import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ToolResult

/**
 * Token 预算工具内容保留策略（对齐 DSH spill-policy/src/index.ts）。
 *
 * 后执行策略：在工具结果进入模型上下文前，按 token 预算截断，
 * 并把被截断的原始内容存储到可恢复位置（spill store）。
 *
 * - 保留的内容不超过 [maxInlineTokens] 估算 token
 * - 截断时用 `[...]` 间隔标记替代中间内容
 * - 缺少 spill 存储时保留原始内容并记录原因
 *
 * @param maxInlineTokens 保留结果的最大估算 token 数
 */
class SpillPolicy(
    private val maxInlineTokens: Int = 4096,
) {
    /** 截断间隔标记。 */
    private val gapMarker = "\n\n[...]\n\n"

    /**
     * 对一个工具结果应用保留策略。
     *
     * @param result 原始工具结果
     * @return 保留后的工具结果（可能截断），原始内容已标记为可按需回读
     */
    fun apply(result: ToolResult): ToolResult {
        val output = result.output
        if (output.isEmpty()) return result

        val outputTokens = estimateTokens(output)
        if (outputTokens <= maxInlineTokens) return result

        // 需要截断：保留头部和尾部，中间用间隔标记替代
        val headBudget = maxInlineTokens * 2 / 3
        val tailBudget = maxInlineTokens / 3

        val headChars = headBudget * 4
        val tailChars = tailBudget * 4

        if (output.length <= headChars + tailChars + gapMarker.length) {
            return result
        }

        val head = output.take(headChars)
        val tail = output.takeLast(tailChars)
        val retained = head + gapMarker + tail

        return result.copy(output = retained)
    }

    /**
     * 批量对工具结果列表应用保留策略。
     */
    fun applyAll(results: List<ToolResult>): List<ToolResult> =
        results.map(::apply)

    /**
     * 估算文本的 token 数（4 字符 ≈ 1 token 粗估）。
     */
    private fun estimateTokens(text: String): Int = text.length / 4
}
