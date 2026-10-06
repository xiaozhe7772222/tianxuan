package top.wkbin.tianxuan.harness.workflow

import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.database.WorkflowRepository
import top.wkbin.tianxuan.core.database.WorkflowScheduleEntity
import top.wkbin.tianxuan.core.database.WorkflowScheduleStore
import top.wkbin.tianxuan.core.model.InstalledDistro
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.core.model.workflow.WorkflowDefinition
import top.wkbin.tianxuan.core.model.workflow.WorkflowEdge
import top.wkbin.tianxuan.core.model.workflow.WorkflowNode
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeType
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState
import top.wkbin.tianxuan.runtime.DownloadProgress
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.RuntimeHealth
import top.wkbin.tianxuan.runtime.RuntimeInstallRequest
import top.wkbin.tianxuan.runtime.RootfsUpdateInfo
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.LinuxSession
import top.wkbin.tianxuan.runtime.shell.ManagedProcess
import top.wkbin.tianxuan.runtime.shell.ProcessType
import top.wkbin.tianxuan.runtime.shell.SessionConfig
import top.wkbin.tianxuan.runtime.shell.ShellCommand

/** WorkflowRunManager：运行时就绪门、启动对账、注册表生命周期。 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowRunManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun definition() = WorkflowDefinition(
        id = "wf_test",
        name = "测试流程",
        description = "",
        category = "测试",
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "触发"),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "完成"),
        ),
        edges = listOf(WorkflowEdge("e1", "start", "output", "done")),
    )

    private fun state(executionId: String, status: WorkflowRunStatus, definition: WorkflowDefinition) =
        WorkflowRuntimeState.initial(executionId, definition).copy(status = status, startedAt = 1L)

    @Test
    fun `start fails fast with FAILED history when runtime is not installed`() = runTest {
        val runtime = FakeLinuxRuntime(initialState = RuntimeState.NotInitialized, restoreResult = false)
        val repository = FakeWorkflowRepository()
        val manager = manager(runtime, repository)

        val result = manager.start(definition(), emptyMap(), "/workspace")

        assertFalse(result.accepted)
        assertNotNull(result.failureReason)
        assertTrue(runtime.restoreCalled)
        val saved = repository.saved.last()
        assertEquals(WorkflowRunStatus.FAILED, saved.state.status)
        assertEquals("MANUAL", saved.triggerSource)
        assertNull(saved.scheduleId)
    }

    @Test
    fun `reconcile marks unfinished runs cancelled and preserves trigger provenance`() = runTest {
        val json = testJson()
        val repository = FakeWorkflowRepository()
        repository.unfinished = listOf(
            logRow(json, state("exec_sch", WorkflowRunStatus.RUNNING, definition()), "SCHEDULE", "sched_1"),
            logRow(json, state("exec_man", WorkflowRunStatus.WAITING_APPROVAL, definition()), "MANUAL", null),
        )
        val manager = manager(FakeLinuxRuntime(), repository)

        manager.reconcileInterruptedRuns()

        assertEquals(2, repository.saved.size)
        repository.saved.forEach { saved ->
            assertEquals(WorkflowRunStatus.CANCELLED, saved.state.status)
            assertNotNull(saved.state.error)
        }
        assertEquals("SCHEDULE", repository.saved[0].triggerSource)
        assertEquals("sched_1", repository.saved[0].scheduleId)
        assertEquals("MANUAL", repository.saved[1].triggerSource)
    }

    @Test
    fun `terminal run releases the running flag`() = runBlocking {
        val repository = FakeWorkflowRepository()
        val manager = manager(FakeLinuxRuntime(initialState = RuntimeState.Ready), repository)

        val result = manager.start(definition(), emptyMap(), "/workspace")
        assertTrue(result.accepted)

        // 空 executor set：TRIGGER 节点立即 FAILED → 运行到终态，观察者随后释放 running 标志
        kotlinx.coroutines.withTimeout(5_000) {
            manager.activeRuns.first { runs -> runs[result.executionId]?.status?.let { it in WorkflowRunManager.TERMINAL } == true }
            manager.running.first { !it }
        }
        assertFalse(manager.running.value)
        assertEquals(WorkflowRunStatus.FAILED, manager.activeRuns.value[result.executionId]?.status)
    }

    @Test
    fun `schedule repository computes next daily run across midnight`() {
        val repository = WorkflowScheduleRepository(
            store = FakeScheduleStore(),
            workflowRepository = FakeWorkflowRepository(),
            dispatcher = RecordingDispatcher(),
        )
        val entity = scheduleEntity(repeatType = "DAILY", hour = 9, minute = 30)
        val from = localTime(2026, 9, 18, 10, 0)
        val next = repository.computeNextRunAt(entity, from)
        val expected = localTime(2026, 9, 19, 9, 30)
        assertEquals(expected, next)

        val before = localTime(2026, 9, 18, 8, 0)
        assertEquals(localTime(2026, 9, 18, 9, 30), repository.computeNextRunAt(entity, before))
    }

    @Test
    fun `schedule repository once trigger ignores past timestamps and clamps interval`() {
        val repository = WorkflowScheduleRepository(
            store = FakeScheduleStore(),
            workflowRepository = FakeWorkflowRepository(),
            dispatcher = RecordingDispatcher(),
        )
        val from = localTime(2026, 9, 18, 10, 0)
        assertNull(repository.computeNextRunAt(scheduleEntity(repeatType = "ONCE", onceAt = from - 1_000), from))
        assertEquals(from + 5_000, repository.computeNextRunAt(scheduleEntity(repeatType = "ONCE", onceAt = from + 5_000), from))
        assertEquals(
            from + 15 * 60_000L,
            repository.computeNextRunAt(scheduleEntity(repeatType = "INTERVAL", intervalMinutes = 3), from),
        )
    }

    @Test
    fun `enabling schedule persists the same next run sent to dispatcher`() = runBlocking {
        val store = FakeScheduleStore().apply {
            saved = scheduleEntity(repeatType = "INTERVAL", intervalMinutes = 15).copy(enabled = false, nextRunAt = 1L)
        }
        val dispatcher = RecordingDispatcher()
        val repository = WorkflowScheduleRepository(store, FakeWorkflowRepository(), dispatcher)

        repository.setEnabled("sched_test", true)

        assertTrue(store.saved!!.enabled)
        assertTrue(store.saved!!.nextRunAt!! > System.currentTimeMillis())
        assertEquals(store.saved, dispatcher.lastDispatched)
        repository.setEnabled("sched_test", false)
        assertNull(store.saved!!.nextRunAt)
        assertEquals(1, dispatcher.cancelled)
    }
}

private fun manager(
    runtime: FakeLinuxRuntime,
    repository: FakeWorkflowRepository,
): WorkflowRunManager = WorkflowRunManager(
    scheduler = WorkflowScheduler(emptySet(), WorkflowApprovalBroker()),
    repository = repository,
    linuxRuntime = runtime,
    json = testJson(),
)

private fun testJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun logRow(json: Json, state: WorkflowRuntimeState, triggerSource: String, scheduleId: String?) =
    top.wkbin.tianxuan.core.database.WorkflowExecutionLogEntity(
        executionId = state.executionId,
        workflowId = state.definition.id,
        startTime = 1L,
        endTime = null,
        status = state.status.name,
        finalContextJson = json.encodeToString(WorkflowRuntimeState.serializer(), state),
        triggerSource = triggerSource,
        workflowName = state.definition.name,
        scheduleId = scheduleId,
    )

private fun scheduleEntity(
    repeatType: String,
    hour: Int? = null,
    minute: Int? = null,
    intervalMinutes: Int? = null,
    onceAt: Long? = null,
): WorkflowScheduleEntity = WorkflowScheduleEntity(
    id = "sched_test",
    workflowId = "wf_test",
    name = "测试计划",
    enabled = true,
    repeatType = repeatType,
    hour = hour,
    minute = minute,
    intervalMinutes = intervalMinutes,
    onceAtEpochMillis = onceAt,
    variablesJson = "{}",
    workspacePath = "/workspace",
    modelId = null,
    modelVariant = null,
    lastExecutionId = null,
    lastRunAt = null,
    nextRunAt = null,
    createdAt = 0L,
)

private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

private class FakeScheduleStore : WorkflowScheduleStore {
    var saved: WorkflowScheduleEntity? = null
    override fun observeSchedules(): Flow<List<WorkflowScheduleEntity>> = MutableStateFlow(emptyList())
    override suspend fun findSchedule(id: String): WorkflowScheduleEntity? = saved?.takeIf { it.id == id }
    override suspend fun upsert(entity: WorkflowScheduleEntity) { saved = entity }
    override suspend fun delete(id: String) = Unit
    override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
    override suspend fun updateRunInfo(id: String, executionId: String?, runAt: Long, nextRunAt: Long?) = Unit
}

private class RecordingDispatcher : WorkflowScheduleDispatcher {
    var dispatched = 0
    var cancelled = 0
    var lastDispatched: WorkflowScheduleEntity? = null
    override fun dispatch(entity: WorkflowScheduleEntity) {
        dispatched++
        lastDispatched = entity
    }

    override fun cancel(scheduleId: String) {
        cancelled++
    }
}

private data class SavedRun(
    val state: WorkflowRuntimeState,
    val triggerSource: String,
    val scheduleId: String?,
)

private class FakeWorkflowRepository : WorkflowRepository {
    val saved = mutableListOf<SavedRun>()
    var unfinished: List<top.wkbin.tianxuan.core.database.WorkflowExecutionLogEntity> = emptyList()

    override fun observeDefinitions(): Flow<List<WorkflowDefinition>> = MutableStateFlow(emptyList())
    override fun observeRecentExecutions(limit: Int): Flow<List<top.wkbin.tianxuan.core.database.WorkflowExecutionLogEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun findById(id: String): WorkflowDefinition? = null
    override suspend fun findBySlashCommand(command: String): WorkflowDefinition? = null
    override suspend fun upsert(definition: WorkflowDefinition) = Unit
    override suspend fun deleteCustom(id: String): Boolean = false
    override suspend fun ensureBuiltins() = Unit
    override suspend fun saveExecution(state: WorkflowRuntimeState, triggerSource: String, scheduleId: String?) {
        saved.add(SavedRun(state, triggerSource, scheduleId))
    }

    override suspend fun findUnfinishedExecutions(): List<top.wkbin.tianxuan.core.database.WorkflowExecutionLogEntity> =
        unfinished

    override suspend fun findExecutionById(id: String): top.wkbin.tianxuan.core.database.WorkflowExecutionLogEntity? = null
}

private class FakeLinuxRuntime(
    initialState: RuntimeState = RuntimeState.NotInitialized,
    private val restoreResult: Boolean = false,
) : LinuxRuntime {
    val stateFlow = MutableStateFlow<RuntimeState>(initialState)
    var restoreCalled = false

    override val state: StateFlow<RuntimeState> = stateFlow
    override val activeDistroId: StateFlow<String> = MutableStateFlow("")
    override val installedDistros: StateFlow<List<InstalledDistro>> = MutableStateFlow(emptyList())

    override suspend fun initialize(request: RuntimeInstallRequest): AppResult<Unit> = error("unused")
    override suspend fun restoreInstalledState(): Boolean {
        restoreCalled = true
        if (restoreResult) stateFlow.value = RuntimeState.Ready
        return restoreResult
    }

    override suspend fun updateRootfs(distroId: String?): AppResult<Unit> = error("unused")
    override suspend fun checkRootfsUpdate(distroId: String?): AppResult<RootfsUpdateInfo> = error("unused")
    override suspend fun healthCheck(distroId: String?): RuntimeHealth = error("unused")
    override suspend fun switchActiveDistro(distroId: String): AppResult<Unit> = error("unused")
    override suspend fun installDistro(
        request: RuntimeInstallRequest,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): AppResult<Unit> = error("unused")

    override suspend fun uninstallDistro(distroId: String): AppResult<Unit> = error("unused")
    override suspend fun resetSandbox(distroId: String?): AppResult<Unit> = error("unused")
    override fun refreshInstalledDistros() = Unit
    override suspend fun execute(command: ShellCommand, distroId: String?): CommandResult = error("unused")
    override suspend fun startSession(config: SessionConfig, distroId: String?): LinuxSession = error("unused")
    override suspend fun startBackground(
        id: String,
        command: ShellCommand,
        toolId: String?,
        type: ProcessType,
        distroId: String?,
    ): ManagedProcess = error("unused")

    override suspend fun stopBackground(id: String): Boolean = true
    override fun listBackground(): List<ManagedProcess> = emptyList()
    override suspend fun cleanupDeadBackground(): Int = 0
    override suspend fun shutdown() = Unit
    override fun rootfsPath(distroId: String?): File = File("/tmp/rootfs")
    override fun workspacePath(): File = File("/tmp/workspace")
}
