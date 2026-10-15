package top.wkbin.tianxuan.workflow

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit
import top.wkbin.tianxuan.core.database.WorkflowScheduleEntity
import top.wkbin.tianxuan.core.model.workflow.WorkflowScheduleRepeat
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduleDispatcher
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduleRepository

/**
 * WorkManager 实现：UniqueWork 以 scheduleId 命名，进程被杀/设备重启后由系统自动
 * 恢复排队。DAILY 用 24h 周期 + 到点 initialDelay（Doze 下可能有分钟级顺延）；
 * INTERVAL 用 N 分钟周期（下限 15 分钟）；ONCE 用一次性延时任务。
 */
class WorkManagerScheduleDispatcher(
    private val context: Context,
) : WorkflowScheduleDispatcher {

    override fun dispatch(entity: WorkflowScheduleEntity) {
        if (!entity.enabled) return
        val workManager = WorkManager.getInstance(context)
        val uniqueName = uniqueWorkName(entity.id)
        val inputData = workDataOf(WorkflowScheduleWorker.KEY_SCHEDULE_ID to entity.id)
        when (WorkflowScheduleRepeat.valueOf(entity.repeatType)) {
            WorkflowScheduleRepeat.DAILY -> {
                val hour = entity.hour ?: return
                val minute = entity.minute ?: return
                val request = PeriodicWorkRequestBuilder<WorkflowScheduleWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(nextDailyDelayMs(hour, minute), TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            WorkflowScheduleRepeat.INTERVAL -> {
                val interval = (entity.intervalMinutes ?: WorkflowScheduleRepository.MIN_INTERVAL_MINUTES)
                    .coerceAtLeast(WorkflowScheduleRepository.MIN_INTERVAL_MINUTES)
                val request = PeriodicWorkRequestBuilder<WorkflowScheduleWorker>(interval.toLong(), TimeUnit.MINUTES)
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
            WorkflowScheduleRepeat.ONCE -> {
                val target = entity.onceAtEpochMillis ?: return
                val delay = (target - System.currentTimeMillis()).coerceAtLeast(0L)
                val request = OneTimeWorkRequestBuilder<WorkflowScheduleWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
            }
            WorkflowScheduleRepeat.WEEKLY -> {
                // 周指定工作日（1=周一...7=周日），每次用 OneTime + 计算下一次匹配日
                val hour = entity.hour ?: return
                val minute = entity.minute ?: return
                val weekDaysStr = entity.weekDays ?: return
                val weekDays = weekDaysStr.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }
                if (weekDays.isEmpty()) return
                val delay = nextWeeklyDelayMs(hour, minute, weekDays)
                val request = OneTimeWorkRequestBuilder<WorkflowScheduleWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
            }
            WorkflowScheduleRepeat.CRON -> {
                // 五字段 Vixie cron：每次 OneTime + 计算下一次匹配时刻
                val cronExpr = entity.cronExpression ?: return
                val delay = top.wkbin.tianxuan.core.model.workflow.CronScheduler
                    .nextOccurrence(cronExpr)?.let { it - System.currentTimeMillis() }?.coerceAtLeast(0L) ?: return
                val request = OneTimeWorkRequestBuilder<WorkflowScheduleWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag(TAG)
                    .build()
                workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }

    /** 距下一个 hour:minute 且在 weekDays 中的工作日的毫秒数。 */
    private fun nextWeeklyDelayMs(hour: Int, minute: Int, weekDays: List<Int>): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Calendar 周日=1..周六=7，weekDays 用 1=周一..7=周日
        // Calendar 日 → weekDays 日：Calendar=1(周日)→weekDays=7, Calendar=2(周一)→weekDays=1
        fun calDowToWeekDay(calDow: Int): Int = if (calDow == 1) 7 else calDow - 1
        // 向前扫描最多 7 天
        for (i in 0..7) {
            if (calDowToWeekDay(cal.get(Calendar.DAY_OF_WEEK)) in weekDays && cal.timeInMillis > now) {
                return cal.timeInMillis - now
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        // 兜底：7 天内必中（weekDays 非空）
        return cal.timeInMillis - now
    }

    override fun cancel(scheduleId: String) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(scheduleId)) }
    }

    /** 距下一个 hour:minute（本地时区）的毫秒数。 */
    private fun nextDailyDelayMs(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis - now
    }

    private fun uniqueWorkName(scheduleId: String) = "workflow_schedule_$scheduleId"

    object ScheduleDispatcherModule {
        fun provideDispatcher(context: Context): WorkflowScheduleDispatcher =
            WorkManagerScheduleDispatcher(context)
    }

    private companion object {
        const val TAG = "workflow_schedule"
    }
}
