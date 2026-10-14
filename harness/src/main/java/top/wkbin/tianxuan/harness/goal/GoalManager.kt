package top.wkbin.tianxuan.harness.goal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Goal 管理器：管理当前会话的显式目标（create/pause/resume/complete/block/clear）。
 *
 * 对齐 DeepSeek Harness 的 goal 机制（goal/change 事件 + projection）：
 * - 每个会话最多一个活跃 Goal（与 DSH 一致）
 * - Goal 有 maxGoalRounds 限制，耗尽后必须 resume 或 complete
 * - 模型声明完成时必须通过 complete() 显式标记（而非自动判定）
 * - 失败/受阻时用 block(reason) 而非静默失败
 */
class GoalManager {
    private val _currentGoal = MutableStateFlow<Goal?>(null)
    val currentGoal: StateFlow<Goal?> = _currentGoal.asStateFlow()

    private val _goalRounds = MutableStateFlow<List<GoalRound>>(emptyList())
    val goalRounds: StateFlow<List<GoalRound>> = _goalRounds.asStateFlow()

    /** 创建新 Goal（当前 Goal 必须为空或已完成）。 */
    fun create(objective: String, maxGoalRounds: Int = 256): GoalResult {
        val current = _currentGoal.value
        if (current != null && current.phase != GoalPhase.COMPLETED) {
            return GoalResult.InvalidTransition(current.phase, listOf(GoalPhase.COMPLETED))
        }
        val goal = Goal(
            objective = objective.trim().takeIf { it.isNotBlank() }
                ?: return GoalResult.InvalidTransition(GoalPhase.ACTIVE, emptyList()),
            maxGoalRounds = maxGoalRounds.coerceIn(1, 10000),
        )
        _currentGoal.value = goal
        _goalRounds.value = emptyList()
        return GoalResult.Success(goal)
    }

    /** 标记当前 Goal 完成（模型显式声明）。 */
    fun complete(): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        if (current.phase == GoalPhase.COMPLETED) {
            return GoalResult.InvalidTransition(GoalPhase.COMPLETED, listOf(GoalPhase.ACTIVE, GoalPhase.PAUSED, GoalPhase.BLOCKED))
        }
        val updated = current.copy(
            phase = GoalPhase.COMPLETED,
            activation = GoalActivation.DISARMED,
            updatedAt = System.currentTimeMillis(),
        )
        _currentGoal.value = updated
        return GoalResult.Success(updated)
    }

    /** 暂停当前 Goal。 */
    fun pause(): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        if (current.phase != GoalPhase.ACTIVE) {
            return GoalResult.InvalidTransition(current.phase, listOf(GoalPhase.ACTIVE))
        }
        val updated = current.copy(
            phase = GoalPhase.PAUSED,
            activation = GoalActivation.DISARMED,
            updatedAt = System.currentTimeMillis(),
        )
        _currentGoal.value = updated
        return GoalResult.Success(updated)
    }

    /** 恢复当前 Goal（armed 重新激活）。 */
    fun resume(): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        if (current.phase !in listOf(GoalPhase.ACTIVE, GoalPhase.PAUSED, GoalPhase.BLOCKED)) {
            return GoalResult.InvalidTransition(current.phase, listOf(GoalPhase.ACTIVE, GoalPhase.PAUSED, GoalPhase.BLOCKED))
        }
        if (current.roundsUsed >= current.maxGoalRounds) {
            return GoalResult.MaxRoundsExceeded(current.roundsUsed, current.maxGoalRounds)
        }
        val updated = current.copy(
            phase = GoalPhase.ACTIVE,
            activation = GoalActivation.ARMED,
            updatedAt = System.currentTimeMillis(),
        )
        _currentGoal.value = updated
        return GoalResult.Success(updated)
    }

    /** 阻塞当前 Goal（遇到无法推进的障碍，记录原因）。 */
    fun block(reason: String): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        if (current.phase != GoalPhase.ACTIVE) {
            return GoalResult.InvalidTransition(current.phase, listOf(GoalPhase.ACTIVE))
        }
        val updated = current.copy(
            phase = GoalPhase.BLOCKED,
            activation = GoalActivation.DISARMED,
            blockedReason = reason.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        _currentGoal.value = updated
        return GoalResult.Success(updated)
    }

    /** 清除当前 Goal（保留历史记录到 goalRounds）。 */
    fun clear(): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        _currentGoal.value = null
        return GoalResult.Success(current)
    }

    /** 消耗一个 round（每轮工具调用对应一个 goal round）。 */
    fun consumeRound(): GoalResult {
        val current = _currentGoal.value ?: return GoalResult.NotFound("no current goal")
        if (current.phase != GoalPhase.ACTIVE) {
            return GoalResult.InvalidTransition(current.phase, listOf(GoalPhase.ACTIVE))
        }
        if (current.roundsUsed >= current.maxGoalRounds) {
            return GoalResult.MaxRoundsExceeded(current.roundsUsed, current.maxGoalRounds)
        }
        val updated = current.copy(
            roundsUsed = current.roundsUsed + 1,
            updatedAt = System.currentTimeMillis(),
        )
        _currentGoal.value = updated
        return GoalResult.Success(updated)
    }

    /** 记录一轮推进的详情。 */
    fun recordRound(toolCallsCount: Int, notes: String = "") {
        val current = _currentGoal.value ?: return
        _goalRounds.update { it + GoalRound(round = current.roundsUsed, startedAt = System.currentTimeMillis(), toolCallsCount = toolCallsCount, notes = notes) }
    }

    /** 重置（新会话或清空） */
    fun reset() {
        _currentGoal.value = null
        _goalRounds.value = emptyList()
    }
}
