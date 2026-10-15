package top.wkbin.tianxuan.harness.jobs

import kotlinx.serialization.Serializable
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 后台作业注册表（对齐 DSH jobs/jobs/src 的 JobRegistry）。
 *
 * 追踪后台（process/子代理/工作流）作业的生命周期：
 * - 注册：唯一 jobId + 所有者 + 起始时间 + 创建工具调用
 * - 结算：终态记录（完成/被杀/失败）+ 原因 + 生产者确认
 * - 终止：可向运行中的作业发起 kill
 *
 * 与现有 process 工具的 PID 管理正交：
 * process 管"操作系统进程"，JobRegistry 管"Harness 作业"（可跨进程聚合）。
 *
 * @param eventLogger 可选事件日志器（写 session 日志）
 */
class JobRegistry(
    private val eventLogger: AgentEventLogger? = null,
) {
    /** 作业终态。 */
    enum class JobStatus { RUNNING, COMPLETED, KILLED, FAILED }

    /** 作业类型。 */
    enum class JobKind { PROCESS, SUBAGENT, WORKFLOW, PTC }

    /** 作业注册数据。 */
    data class JobRegisteredData(
        val jobId: String,
        val owner: String,
        val kind: JobKind,
        val label: String,
        val startedAt: Long,
        val toolCallId: String? = null,
    )

    /** 作业结算数据。 */
    data class JobSettledData(
        val jobId: String,
        val owner: String,
        val kind: JobKind,
        val label: String,
        val startedAt: Long,
        val finishedAt: Long,
        val status: JobStatus,
        val cause: String,
        val producerReported: Boolean,
        val detail: String? = null,
    )

    /** 一个活动作业。 */
    class Job(
        val jobId: String,
        val owner: String,
        val kind: JobKind,
        val label: String,
        val startedAt: Long,
        val toolCallId: String?,
    ) {
        @Volatile var status: JobStatus = JobStatus.RUNNING
            internal set
        @Volatile var detail: String? = null
            internal set
        @Volatile var finishedAt: Long? = null
            internal set
        /** 终止请求处理器（由创建方注册）。 */
        @Volatile internal var killHandler: (() -> Boolean)? = null
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    private val seq = AtomicLong(0)

    /**
     * 注册一个新作业。
     *
     * @param owner 所有者会话 ID
     * @param kind 作业类型
     * @param label 作业标签
     * @param toolCallId 创建工具调用 ID（若有）
     * @return 已注册的作业
     */
    fun register(
        owner: String,
        kind: JobKind,
        label: String,
        toolCallId: String? = null,
    ): Job {
        val jobId = UUID.randomUUID().toString()
        val job = Job(
            jobId = jobId,
            owner = owner,
            kind = kind,
            label = label,
            startedAt = System.currentTimeMillis(),
            toolCallId = toolCallId,
        )
        jobs[jobId] = job
        logDispatch("job/registered", jobId, owner, kind, label, "status=RUNNING")
        return job
    }

    /**
     * 注册一个带外部 id 的作业（如已有的 process id）。
     */
    fun registerWithId(
        jobId: String,
        owner: String,
        kind: JobKind,
        label: String,
        toolCallId: String? = null,
    ): Job {
        val job = Job(
            jobId = jobId,
            owner = owner,
            kind = kind,
            label = label,
            startedAt = System.currentTimeMillis(),
            toolCallId = toolCallId,
        )
        jobs[jobId] = job
        logDispatch("job/registered", jobId, owner, kind, label, "status=RUNNING")
        return job
    }

    /**
     * 结算一个作业。
     *
     * @param jobId 作业 ID
     * @param status 终态
     * @param cause 结算原因
     * @param producerReported 生产者是否报告了终态
     * @param detail 终态详情
     * @return 结算成功返回 true；作业不存在返回 false
     */
    fun settle(
        jobId: String,
        status: JobStatus,
        cause: String,
        producerReported: Boolean = true,
        detail: String? = null,
    ): Boolean {
        val job = jobs[jobId] ?: return false
        if (job.status != JobStatus.RUNNING) return false // 幂等：已结算
        job.status = status
        job.detail = detail
        job.finishedAt = System.currentTimeMillis()
        logDispatch(
            "job/settled", jobId, job.owner, job.kind, job.label,
            "status=$status cause=$cause detail=${detail ?: ""}",
        )
        return true
    }

    /**
     * 终止一个运行中的作业。
     *
     * @param jobId 作业 ID
     * @param reason 终止原因
     * @return 成功终止返回 true；作业不存在或已结算返回 false
     */
    fun kill(jobId: String, reason: String): Boolean {
        val job = jobs[jobId] ?: return false
        if (job.status != JobStatus.RUNNING) return false
        // 调用创建方注册的终止处理器
        val handler = job.killHandler
        if (handler != null) {
            val killed = runCatching { handler() }.getOrDefault(false)
            if (!killed) return false
        }
        return settle(jobId, JobStatus.KILLED, "killed", producerReported = false, detail = reason)
    }

    /**
     * 获取一个作业。
     */
    fun get(jobId: String): Job? = jobs[jobId]

    /**
     * 列出所有活动（RUNNING）作业。
     */
    fun activeJobs(owner: String? = null): List<Job> {
        val all = jobs.values.filter { it.status == JobStatus.RUNNING }
        return if (owner == null) all else all.filter { it.owner == owner }
    }

    /**
     * 列出所有作业（含已结算）。
     */
    fun allJobs(owner: String? = null): List<Job> {
        val all = jobs.values.toList()
        return if (owner == null) all else all.filter { it.owner == owner }
    }

    /**
     * 为作业注册终止处理器。
     */
    fun onKill(jobId: String, handler: () -> Boolean) {
        jobs[jobId]?.killHandler = handler
    }

    /**
     * 清理已结算的旧作业（保留最近 [maxRetained] 个）。
     */
    fun prune(maxRetained: Int = 100) {
        val settled = jobs.values.filter { it.status != JobStatus.RUNNING }
            .sortedByDescending { it.finishedAt ?: 0 }
        val toRemove = settled.drop(maxRetained)
        for (job in toRemove) {
            jobs.remove(job.jobId)
        }
    }

    /**
     * 移除一个作业记录（即使运行中，用于会话删除）。
     */
    fun remove(jobId: String) {
        jobs.remove(jobId)
    }

    private fun logDispatch(
        tag: String,
        jobId: String,
        owner: String,
        kind: JobKind,
        label: String,
        detail: String,
    ) {
        // 事件日志受用户开关门控；日志失败不影响作业生命周期
        // (suspended 函数，调用方无需等待)
    }
}