package top.wkbin.tianxuan.harness.session

import top.wkbin.tianxuan.harness.recovery.RecoveryManager

/**
 * 会话崩溃恢复（对齐 DSH session/src/repair.ts）。
 *
 * 崩溃恢复时，对未正常收尾的会话合成 `interrupted` 轮次结束标记：
 * - 会话日志中最后一条消息如果是 assistant（模型正在生成或等待工具结果时进程死亡）
 *   → 该轮没有正常结束，合成 TurnEndReason.Interrupted
 * - 会话日志中最后一条消息如果是 tool_call（工具正在执行时进程死亡）
 *   → 该轮没有正常结束，合成 TurnEndReason.Interrupted
 * - 会话日志中最后一条消息如果是 tool_result 或 user（轮次已正常闭环）
 *   → 无需合成
 *
 * 合成的 interrupted 标记是追认的——它不改变已写入的消息，
 * 只给崩溃场景一个明确的结束归因，让恢复/压缩/回溯可精确区分
 * "正常完成"与"进程死亡"。
 *
 * 天玄当前没有 turn/start/turn/end 事件（消息模型是 flat 的），
 * 恢复逻辑通过消息类型推断轮次状态。
 */
object SessionRepair {

    /**
     * 检查会话的最后一条消息是否表示该轮未正常结束，
     * 需要合成 interrupted 标记。
     *
     * @param lastMessage 会话的最后一条消息
     * @return 如果需要 interrupted 标记则返回 true
     */
    fun needsInterruptedMarker(lastMessage: top.wkbin.tianxuan.harness.HarnessMessage?): Boolean {
        if (lastMessage == null) return false
        return when (lastMessage) {
            // 模型正在生成或等待工具结果时进程死亡 → 轮次未正常结束
            is top.wkbin.tianxuan.harness.AssistantText -> true
            // 工具正在执行时进程死亡 → 轮次未正常结束
            is top.wkbin.tianxuan.harness.ToolCall -> true
            // 轮次已正常闭环（tool_result → 模型已收到结果）
            is top.wkbin.tianxuan.harness.ToolResult -> false
            // 用户消息之后必然有 assistant 跟进 → 无需标记
            is top.wkbin.tianxuan.harness.UserMessage -> false
            // UI-only 事件不影响轮次状态
            else -> false
        }
    }

    /**
     * 为崩溃会话合成 TurnEndReason.Interrupted。
     *
     * @param sessionId 会话 ID
     * @param lastMessage 会话的最后一条消息
     * @return 合成的 TurnEndReason；如果不需要则返回 null
     */
    fun synthesizeInterruptedReason(
        sessionId: String,
        lastMessage: top.wkbin.tianxuan.harness.HarnessMessage?,
    ): TurnEndReason? {
        return if (needsInterruptedMarker(lastMessage)) {
            TurnEndReason.Interrupted
        } else {
            null
        }
    }
}
