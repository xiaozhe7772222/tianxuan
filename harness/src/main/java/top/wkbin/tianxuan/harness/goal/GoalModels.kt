package top.wkbin.tianxuan.harness.goal

import java.util.UUID

/** Goal 状态：与 DeepSeek Harness 的 goal/phase 对齐。 */
enum class GoalPhase {
    /** 活跃：正在推进中 */
    ACTIVE,
    /** 暂停：用户或模型主动暂停，可 resume */
    PAUSED,
    /** 阻塞：遇到无法推进的障碍，记录原因，可 resume */
    BLOCKED,
    /** 完成：模型声明完成且验证通过，goal 终止 */
    COMPLETED,
}

/** Goal 激活状态：armed 表示允许自动续跑，disarmed 表示需用户/策略介入。 */
enum class GoalActivation {
    ARMED,
    DISARMED,
}

/** 一次 Goal 推进的记录（round = 一轮模型思考+工具执行）。 */
data class GoalRound(
    val round: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val toolCallsCount: Int = 0,
    val notes: String = "",
)

/** 当前活跃 Goal。 */
data class Goal(
    val id: String = "goal-${UUID.randomUUID()}",
    val objective: String,
    val phase: GoalPhase = GoalPhase.ACTIVE,
    val activation: GoalActivation = GoalActivation.ARMED,
    val maxGoalRounds: Int = 256,
    val roundsUsed: Int = 0,
    val blockedReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Goal 操作结果。 */
sealed class GoalResult {
    data class Success(val goal: Goal) : GoalResult()
    data class NotFound(val goalId: String) : GoalResult()
    data class InvalidTransition(val current: GoalPhase, val allowed: List<GoalPhase>) : GoalResult()
    data class StaleRevision(val expected: Int, val actual: Int) : GoalResult()
    data class MaxRoundsExceeded(val roundsUsed: Int, val maxRounds: Int) : GoalResult()
}
