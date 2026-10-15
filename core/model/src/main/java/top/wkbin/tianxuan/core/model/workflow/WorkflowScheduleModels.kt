package top.wkbin.tianxuan.core.model.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 定时计划的重复语义：
 * DAILY=每天固定时刻；INTERVAL=每 N 分钟（WorkManager 周期下限 15 分钟）；ONCE=一次性延时；
 * WEEKLY=每周指定工作日（逗号分隔 1-7, 1=周一）；CRON=五字段 Vixie cron 表达式。
 *
 * WEEKLY 和 CRON 对齐 DeepSeek Harness schedule/domain.ts 的日历规则。
 */
enum class WorkflowScheduleRepeat { DAILY, INTERVAL, ONCE, WEEKLY, CRON }

/** 一次工作流运行的触发来源：用户手动启动，或定时计划到点拉起。 */
@Serializable
sealed class WorkflowRunTrigger {
    @Serializable
    @SerialName("manual")
    data object Manual : WorkflowRunTrigger()

    @Serializable
    @SerialName("schedule")
    data class Schedule(val scheduleId: String) : WorkflowRunTrigger()
}

val WorkflowRunTrigger.triggerSource: String
    get() = if (this is WorkflowRunTrigger.Schedule) "SCHEDULE" else "MANUAL"
