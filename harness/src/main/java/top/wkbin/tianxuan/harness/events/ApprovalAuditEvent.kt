package top.wkbin.tianxuan.harness.events

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 审批审计事件（对齐 DSH interaction/user-approval/src/types.ts）。
 *
 * 将审批决策持久化为结构化审计事件，写入会话日志：
 * - approval/asked: 审批问题被提出（含工具名、原因、调用 ID）
 * - approval/decided: 审批结果（allowed-once / rejected / cancelled / unavailable）
 *
 * 这些事件是 log-only 审计记录，不进入模型可见 surface，
 * 但可供追溯、排错与安全审计。
 */
@Serializable
data class ApprovalAuditEvent(
    val id: String,
    val createdAt: Long,
    /** 审批请求 ID（服务发放，每次 request 一个新 id）。 */
    val requestId: String,
    /** 工具名。 */
    val toolName: String,
    /** 触发审批的工具调用 ID（若有）。 */
    val toolCallId: String? = null,
    /** 审批发起原因（人话说明）。 */
    val reason: String = "",
    /** 审批结果；asked 阶段为 null。 */
    val outcome: ApprovalOutcome? = null,
)

/** 闭合审批结果。 */
@Serializable
enum class ApprovalOutcome {
    /** 一次性允许。 */
    ALLOWED_ONCE,
    /** 显式拒绝。 */
    REJECTED,
    /** 用户撤回。 */
    CANCELLED,
    /** 回答者不可用。 */
    UNAVAILABLE,
}

/**
 * 审批审计写入器（对齐 DSH ApprovalService 的事件发射）。
 *
 * 天玄的 ApprovalPolicyEngine 在决定是否需要审批时调用
 * [logAsked]，在获得用户决策后调用 [logDecided]。
 *
 * 实现注意：事件通过 [HarnessEventBus] 分发到持久化层，
 * 由 SessionTreeStore.append 落库。
 */
class ApprovalAuditWriter(
    private val eventBus: HarnessEventBus,
) {
    /**
     * 记录审批问题被提出。
     *
     * @return 审批请求 ID（用于后续 logDecided 关联）
     */
    fun logAsked(
        sessionId: String,
        toolName: String,
        toolCallId: String? = null,
        reason: String = "",
    ): String {
        val requestId = UUID.randomUUID().toString()
        eventBus.emit(
            HarnessEvent.ApprovalAsked(
                sessionId = sessionId,
                requestId = requestId,
                toolName = toolName,
                toolCallId = toolCallId,
                reason = reason,
                timestamp = System.currentTimeMillis(),
            ),
        )
        return requestId
    }

    /**
     * 记录审批结果。
     */
    fun logDecided(
        sessionId: String,
        requestId: String,
        outcome: ApprovalOutcome,
    ) {
        eventBus.emit(
            HarnessEvent.ApprovalDecided(
                sessionId = sessionId,
                requestId = requestId,
                outcome = outcome.name,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}