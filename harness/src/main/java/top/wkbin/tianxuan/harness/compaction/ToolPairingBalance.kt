package top.wkbin.tianxuan.harness.compaction

import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolResult

/**
 * 工具配对平衡（对齐 DSH compaction/src/tool-pairing.ts）。
 *
 * 压缩改变 surface 位置，安全的切口从当前 surface 序的消息内容推导，
 * 而非 step 标记。本对象计算每个可能的切口位置是否"平衡"
 * （该切口前的所有 tool_call 都有对应的 tool_result，没有悬空调用）。
 *
 * 用法：在 [CompactionManager.compact] 中选择 keepFromIndex 前，
 * 调用 [findBalancedCut] 将 keepFromIndex 调整到最近的平衡位置。
 */
object ToolPairingBalance {

    /**
     * 计算消息列表中每个切口位置的平衡状态。
     *
     * @param messages 当前 surface 上的消息列表（有序）
     * @return 长度 messages.size + 1 的布尔数组；
     *   entry[i] = true 表示在 messages[i] 之前切口是平衡的
     *   （切口前的所有 tool_call 都有对应的 tool_result）；
     *   最后一个 entry 表示在所有消息之后切口是否平衡。
     */
    fun cutBalanced(messages: List<HarnessMessage>): BooleanArray {
        val result = BooleanArray(messages.size + 1)
        val answeredCallIds = messages.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }
        var inProgressToolCalls = 0

        result[0] = true // 空列表切口总是平衡的

        for (i in messages.indices) {
            when (val msg = messages[i]) {
                is ToolCall -> {
                    if (msg.id in answeredCallIds) {
                        // 有对应的 result，不会影响平衡
                    } else {
                        inProgressToolCalls++
                    }
                }
                is ToolResult -> {
                    if (inProgressToolCalls > 0) inProgressToolCalls--
                }
                else -> { /* 不改变工具调用计数 */ }
            }
            result[i + 1] = inProgressToolCalls == 0
        }

        return result
    }

    /**
     * 在消息列表中找到最近的平衡切口位置。
     *
     * 从请求的 [preferredIndex] 开始向前搜索，找到第一个平衡的切口位置。
     * 如果向前找不到，向后搜索。
     *
     * @param messages 当前 surface 上的消息列表
     * @param preferredIndex 请求的切口位置（1-based：保留从该索引开始的消息）
     * @return 调整后的平衡切口位置；如果找不到平衡位置则返回原始 preferredIndex
     */
    fun findBalancedCut(messages: List<HarnessMessage>, preferredIndex: Int): Int {
        if (preferredIndex < 1 || preferredIndex > messages.size) return preferredIndex
        val balanced = cutBalanced(messages)

        // 向前搜索（保留更多消息）
        for (i in preferredIndex downTo 1) {
            if (balanced[i]) return i
        }

        // 向后搜索（保留更少消息）
        for (i in (preferredIndex + 1)..messages.size) {
            if (balanced[i]) return i
        }

        // 找不到平衡位置：返回原始位置（可能切断了 tool_call/result 对）
        return preferredIndex
    }

    /**
     * 检查指定切口位置是否平衡（该切口前无悬空 tool_call）。
     */
    fun isBalancedAt(messages: List<HarnessMessage>, index: Int): Boolean {
        if (index < 0 || index > messages.size) return false
        return cutBalanced(messages)[index]
    }
}
