package top.wkbin.tianxuan.harness.projection

import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.UserMessage

/**
 * 计划模式投影（对齐 DSH plan/plan-mode 的 PlanProjection）。
 *
 * 从 plan 工具调用与用户消息推导物化 PLAN 状态：
 * - active：当前在 PLAN 模式（最近一次 plan 工具调用带有 replace_active）
 * - pending：正在切换到 PLAN 但尚未完成（进行中的用户轮）
 *
 * 供 UI 显示「计划模式」标识与 HarnessLoop 跳转规划分支。
 */
object PlanModeProjection : ProjectionDefinition<String, PlanModeProjection.PlanUnitState> {

    override val key: String = "plan"

    /** 计划投影的线上值。 */
    data class PlanProjection(
        /** 已记录的计划模式（第一个 plan/mode 之前为非活跃）。 */
        val active: Boolean,
        /** 进行中的切换（计划工具调用后尚未到达新的用户轮）。 */
        val pending: Boolean,
    )

    /** 宿主状态。 */
    data class PlanUnitState(
        /** 已记录的计划模式。 */
        val active: Boolean = false,
        /** 切换的目标模式；无进行中切换时为 null。 */
        val wanted: Boolean? = null,
        /** 最近一次计划工具调用的消息索引。 */
        val lastPlanCallIndex: Int? = null,
        /** 最近一次用户消息索引。 */
        val lastUserMessageIndex: Int? = null,
    )

    override fun init(header: ProjectionSessionMeta, inheritedEventCount: Int): PlanUnitState = PlanUnitState()

    override fun apply(state: PlanUnitState, event: HarnessMessage): PlanUnitState {
        return when (event) {
            is top.wkbin.tianxuan.harness.ToolCall -> {
                val toolName = event.rawToolName ?: event.tool.name.lowercase()
                if (toolName == "plan") {
                    // 检查参数是否包含 replace_active（PLAN 模式激活）或 clear（退出）
                    val args = event.args.toString()
                    val wantsPlan = args.contains("replace_active")
                    val wantsClear = args.contains("clear") || args.contains("delete_active")
                    val newActive = when {
                        wantsClear -> false
                        wantsPlan -> true
                        else -> state.active // 更新/查看类操作不改模式
                    }
                    // wanted 记录"切换目标"，active 保持旧值直到下一个 UserMessage 才生效。
                    // 同时更新两者会导致 pending 永远为 false（wanted == active 恒成立）。
                    state.copy(wanted = newActive, lastPlanCallIndex = state.lastPlanCallIndex)
                } else {
                    state
                }
            }
            is UserMessage -> {
                // 新的用户轮到来，进行中的切换已生效：active 采纳 wanted，wanted 清空
                state.copy(
                    active = state.wanted ?: state.active,
                    wanted = null,
                    lastUserMessageIndex = state.lastUserMessageIndex,
                )
            }
            else -> state
        }
    }

    override fun view(state: PlanUnitState): Any? = PlanProjection(
        active = state.active,
        pending = state.wanted != null && state.wanted != state.active,
    )
}