package top.wkbin.tianxuan.harness.workflow

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import top.wkbin.tianxuan.core.database.WorkflowRepository
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalRequest
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState
import top.wkbin.tianxuan.core.model.workflow.WorkflowDefinition
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunTrigger
import top.wkbin.tianxuan.core.model.workflow.triggerSource
import top.wkbin.tianxuan.runtime.LinuxRuntime

/**
 * 工作流运行的进程级所有者：把执行从 ViewModel 生命周期解耦。
 *
 * - 所有运行跑在自建的 [runScope]（SupervisorJob），退出页面/销毁 ViewModel 不再取消；
 * - 注册表持有 [WorkflowRunHandle]，UI 或通知栏可随时 cancel/decide；
 * - 运行随推进持续 upsert RUNNING 面包屑（进程被杀后历史可对账），终态写最终快照；
 * - [reconcileInterruptedRuns] 在应用启动时把进程死亡遗留的非终态行标为 CANCELLED。
 */
class WorkflowRunManager(
    private val scheduler: WorkflowScheduler,
    private val repository: WorkflowRepository,
    private val linuxRuntime: LinuxRuntime,
    private val json: Json,
) {
    internal constructor(
        scheduler: WorkflowScheduler,
        repository: WorkflowRepository,
        linuxRuntime: LinuxRuntime,
        json: Json,
        scopeOverride: CoroutineScope,
    ) : this(scheduler, repository, linuxRuntime, json) {
        runScopeOverride = scopeOverride
    }

    private val defaultRunScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runScopeOverride: CoroutineScope? = null
    private val runScope: CoroutineScope get() = runScopeOverride ?: defaultRunScope

    private val handles = ConcurrentHashMap<String, WorkflowRunHandle>()
    private val runningIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _activeRuns = MutableStateFlow<Map<String, WorkflowRuntimeState>>(emptyMap())
    val activeRuns: StateFlow<Map<String, WorkflowRuntimeState>> = _activeRuns.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _latestRunId = MutableStateFlow<String?>(null)
    val latestRunId: StateFlow<String?> = _latestRunId.asStateFlow()

    /** 全部待审批请求（executionId → request）：后台审批通知与 UI 弹窗共用。 */
    private val _pendingApprovals = MutableStateFlow<Map<String, WorkflowApprovalRequest>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, WorkflowApprovalRequest>> = _pendingApprovals.asStateFlow()

    fun handle(executionId: String): WorkflowRunHandle? = handles[executionId]

    /** 指定运行的审批请求流；运行不存在时返回恒为 null 的空流。 */
    fun approvalRequestFor(executionId: String): StateFlow<WorkflowApprovalRequest?> =
        handles[executionId]?.approvalRequest ?: MutableStateFlow(null)

    fun cancel(executionId: String) {
        handles[executionId]?.cancel()
        stopManagedProcesses(executionId)
    }

    fun decide(executionId: String, nodeId: String, approved: Boolean, variables: Map<String, String> = emptyMap()): Boolean =
        handles[executionId]?.decide(nodeId, approved, variables) ?: false

    /**
     * 启动一次运行。运行时未就绪（未安装沙箱/等待超时）时不启动调度，
     * 而是落一条 FAILED 历史便于用户与定时计划看到原因。
     */
    suspend fun start(
        definition: WorkflowDefinition,
        variables: Map<String, String> = emptyMap(),
        workspacePath: String,
        trigger: WorkflowRunTrigger = WorkflowRunTrigger.Manual,
    ): WorkflowStartResult {
        if (linuxRuntime.state.value !is RuntimeState.Ready) {
            awaitRuntimeReady()?.let { failure ->
                val executionId = "wf_${UUID.randomUUID()}"
                val failedState = WorkflowRuntimeState.initial(executionId, definition).copy(
                    status = WorkflowRunStatus.FAILED,
                    startedAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                    error = failure,
                )
                runCatching {
                    repository.saveExecution(failedState, trigger.triggerSource, (trigger as? WorkflowRunTrigger.Schedule)?.scheduleId)
                }
                return WorkflowStartResult(executionId, failure)
            }
        }
        val handle = scheduler.execute(definition, variables, workspacePath, runScope)
        val executionId = handle.state.value.executionId
        handles[executionId] = handle
        runningIds.add(executionId)
        _running.value = true
        _latestRunId.value = executionId
        runScope.launch { observeRun(handle, trigger) }
        return WorkflowStartResult(executionId)
    }

    /** 启动对账：进程死亡遗留的非终态历史补写 CANCELLED，节点状态与日志保留可回看。 */
    suspend fun reconcileInterruptedRuns() {
        val unfinished = runCatching { repository.findUnfinishedExecutions() }.getOrDefault(emptyList())
        unfinished.forEach { row ->
            val state = runCatching { json.decodeFromString<WorkflowRuntimeState>(row.finalContextJson) }.getOrNull() ?: return@forEach
            if (state.status in TERMINAL) return@forEach
            val reconciled = state.copy(
                status = WorkflowRunStatus.CANCELLED,
                finishedAt = System.currentTimeMillis(),
                error = "进程曾被系统终止，可重新运行",
            )
            runCatching { repository.saveExecution(reconciled, row.triggerSource, row.scheduleId) }
        }
    }

    private suspend fun observeRun(handle: WorkflowRunHandle, trigger: WorkflowRunTrigger) {
        val executionId = handle.state.value.executionId
        val scheduleId = (trigger as? WorkflowRunTrigger.Schedule)?.scheduleId
        val source = trigger.triggerSource
        var lastPersistAt = 0L
        // 审批流订阅必须与本观察协程同生共死：否则每次运行泄漏一个挂在 StateFlow 上的收集协程
        val approvalJob = runScope.launch {
            handle.approvalRequest.collect { request ->
                _pendingApprovals.update { current ->
                    if (request == null) current - executionId else current + (executionId to request)
                }
            }
        }
        try {
            handle.state.first { state ->
                _activeRuns.update { it + (executionId to state) }
                val terminal = state.status in TERMINAL
                val now = System.currentTimeMillis()
                // 首帧必写 RUNNING 面包屑；此后按间隔节流；终态必写最终快照
                if (terminal || lastPersistAt == 0L || now - lastPersistAt >= BREADCRUMB_INTERVAL_MS) {
                    lastPersistAt = now
                    runCatching { repository.saveExecution(state, source, scheduleId) }
                }
                terminal
            }
        } finally {
            approvalJob.cancel()
            finishRun(executionId)
        }
    }

    private fun finishRun(executionId: String) {
        if (runningIds.remove(executionId)) {
            _running.value = runningIds.isNotEmpty()
            _pendingApprovals.update { it - executionId }
            stopManagedProcesses(executionId)
            // 终态句柄保留一段时间供 UI 回看，之后从注册表与 activeRuns 中回收
            runScope.launch {
                delay(RECLAIM_DELAY_MS)
                handles.remove(executionId)
                _activeRuns.update { it - executionId }
            }
        }
    }

    /** 回收该运行通过 PROCESS_SERVICE 节点启动的托管后台进程。 */
    private fun stopManagedProcesses(executionId: String) {
        runCatching {
            linuxRuntime.listBackground()
                .filter { it.id.startsWith("workflow:$executionId:") }
                .forEach { process -> launchStop(process.id) }
        }
    }

    private fun launchStop(processId: String) {
        runScope.launch { runCatching { linuxRuntime.stopBackground(processId) } }
    }

    /** 返回 null 表示就绪；否则返回用户可读的失败原因。不自动下载 RootFS。 */
    private suspend fun awaitRuntimeReady(): String? {
        val state = linuxRuntime.state
        if (state.value is RuntimeState.NotInitialized) {
            val restored = runCatching { linuxRuntime.restoreInstalledState() }.getOrDefault(false)
            if (!restored && state.value !is RuntimeState.Ready) {
                return "Linux 沙箱尚未初始化，无法运行工作流"
            }
        }
        val ready = withTimeoutOrNull(RUNTIME_READY_TIMEOUT_MS) {
            state.first { it is RuntimeState.Ready }
        } ?: return "等待 Linux 沙箱就绪超时（${RUNTIME_READY_TIMEOUT_MS / 1000} 秒），工作流未执行"
        return null
    }

    data class WorkflowStartResult(
        val executionId: String,
        val failureReason: String? = null,
    ) {
        val accepted: Boolean get() = failureReason == null
    }

    companion object {
        val TERMINAL = setOf(WorkflowRunStatus.SUCCESS, WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED)
        private const val BREADCRUMB_INTERVAL_MS = 3_000L
        private const val RECLAIM_DELAY_MS = 60 * 60 * 1000L
        private const val RUNTIME_READY_TIMEOUT_MS = 2 * 60 * 1000L
    }
}
