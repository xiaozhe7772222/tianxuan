package top.wkbin.tianxuan.harness.approval

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity
import top.wkbin.tianxuan.core.database.AgentApprovalRepository
import top.wkbin.tianxuan.core.database.AppDatabase
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.database.RoomHarnessRuntimeRepository
import top.wkbin.tianxuan.core.database.RoomHarnessSessionRepository
import top.wkbin.tianxuan.harness.ApprovalPolicyEngine
import top.wkbin.tianxuan.harness.AskUserQuestions
import top.wkbin.tianxuan.harness.events.HarnessEventBus
import top.wkbin.tianxuan.harness.operation.OperationCoordinator

/**
 * 审批与提问（ask_user）超时处理与自愈测试：
 * 验证会话级超时扫描（sweepExpiredForSession）、超时有效性裁决与状态恢复。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HarnessApprovalTimeoutTest {

    private lateinit var database: AppDatabase
    private lateinit var coordinator: OperationCoordinator
    private lateinit var policy: ApprovalResumePolicy
    private lateinit var approvalRepo: AgentApprovalRepository

    private val nowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val runtimeRepo = RoomHarnessRuntimeRepository(database.harnessRuntimeDao())
        coordinator = OperationCoordinator(runtimeRepo, Json { ignoreUnknownKeys = true }, HarnessEventBus())
        approvalRepo = AgentApprovalRepository(database.agentApprovalDao())
        policy = ApprovalResumePolicy(
            sessionDao = RoomHarnessSessionRepository(database.harnessSessionDao()),
            operationCoordinator = coordinator,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun approvalRequest(
        id: String,
        sessionId: String = "s1",
        toolName: String = "base",
        expiresAt: Long,
    ) = AgentApprovalRequestEntity(
        id = id,
        sessionId = sessionId,
        toolCallId = "call-$id",
        toolName = toolName,
        argumentsJson = """{"command":"echo test"}""",
        workspace = "/workspace/proj",
        riskLevel = "MEDIUM",
        reason = "执行命令",
        summary = "echo test",
        status = AgentApprovalRequestEntity.STATUS_PENDING,
        createdAt = nowMs - 60_000L,
        argsHash = ApprovalPolicyEngine.argsHash("""{"command":"echo test"}"""),
        expiresAt = expiresAt,
    )

    private suspend fun seedSession(id: String) {
        database.harnessSessionDao().upsert(
            HarnessSessionEntity(
                id = id,
                title = "test",
                createdAt = nowMs,
                updatedAt = nowMs,
                modelId = null,
                workspace = "/workspace/proj",
                projectType = "",
                approvalMode = "full_access",
            ),
        )
    }

    @Test
    fun `sweepExpiredForSession only claims expired requests for the target session`() = runBlocking {
        seedSession("s1")
        seedSession("s2")

        // s1: 1 个已过期，1 个未过期
        val expiredS1 = approvalRequest("req-s1-expired", sessionId = "s1", expiresAt = nowMs - 1000L)
        val pendingS1 = approvalRequest("req-s1-pending", sessionId = "s1", expiresAt = nowMs + 60_000L)

        // s2: 1 个已过期
        val expiredS2 = approvalRequest("req-s2-expired", sessionId = "s2", expiresAt = nowMs - 1000L)

        approvalRepo.create(expiredS1)
        approvalRepo.create(pendingS1)
        approvalRepo.create(expiredS2)

        val swept = approvalRepo.sweepExpiredForSession("s1", now = nowMs)

        // 仅扫出并认领 s1 的过期请求
        assertEquals(1, swept.size)
        assertEquals("req-s1-expired", swept[0].id)
        assertEquals(AgentApprovalRequestEntity.STATUS_EXPIRED, swept[0].status)

        // 检查数据库持久化状态
        assertEquals(AgentApprovalRequestEntity.STATUS_EXPIRED, approvalRepo.find("req-s1-expired")?.status)
        assertEquals(AgentApprovalRequestEntity.STATUS_PENDING, approvalRepo.find("req-s1-pending")?.status)
        assertEquals(AgentApprovalRequestEntity.STATUS_PENDING, approvalRepo.find("req-s2-expired")?.status)

        // s1 仍有一个未过期的 pending
        assertEquals(1, approvalRepo.pendingNow("s1", now = nowMs).size)
    }

    @Test
    fun `ask_user request timeout resolves to expired claim status and invalidation reason`() = runBlocking {
        seedSession("s1")

        val askUserReq = approvalRequest(
            id = "req-ask-1",
            sessionId = "s1",
            toolName = AskUserQuestions.TOOL_NAME,
            expiresAt = nowMs - 1L,
        )
        approvalRepo.create(askUserReq)

        val verdict = policy.evaluate(askUserReq, approved = true, nowMs = nowMs)

        assertTrue("提问过期裁决应判定为无效", verdict.isInvalid)
        assertTrue(verdict.invalidationReason!!.contains("过期"))
        assertEquals(AgentApprovalRequestEntity.STATUS_EXPIRED, verdict.claimStatus)
    }

    @Test
    fun `all approvals expired clears pendingNow for session`() = runBlocking {
        seedSession("s1")

        val expiredReq = approvalRequest("req-1", sessionId = "s1", expiresAt = nowMs - 5000L)
        approvalRepo.create(expiredReq)

        val swept = approvalRepo.sweepExpiredForSession("s1", nowMs)
        assertEquals(1, swept.size)

        // 清扫后 pendingNow 应当为空，指示该会话已无可等待的审批
        val remaining = approvalRepo.pendingNow("s1", now = nowMs)
        assertTrue("所有审批过期后 pendingNow 应当为空", remaining.isEmpty())
    }

    @Test
    fun `pendingNow is pure and does not mutate status of expired requests across sessions`() = runBlocking {
        seedSession("s1")
        seedSession("s2")

        val expiredS1 = approvalRequest("req-s1-exp", sessionId = "s1", expiresAt = nowMs - 1000L)
        val expiredS2 = approvalRequest("req-s2-exp", sessionId = "s2", expiresAt = nowMs - 1000L)

        approvalRepo.create(expiredS1)
        approvalRepo.create(expiredS2)

        // 调用 s1 的 pendingNow
        val pendingS1 = approvalRepo.pendingNow("s1", now = nowMs)
        assertTrue("已过期的审批不应出现在 pendingNow 中", pendingS1.isEmpty())

        // 检查数据库：s2 的记录绝不能被 s1 的 pendingNow 附带副作用静默置为 expired
        val dbS2 = approvalRepo.find("req-s2-exp")
        assertEquals(
            "pendingNow 应当为纯查询，不能提前污染其他会话的数据库状态",
            AgentApprovalRequestEntity.STATUS_PENDING,
            dbS2?.status,
        )

        // 稍后 s2 自身运行 sweepExpiredForSession 时，依然能够正确认领并获得被过期的实体列表
        val sweptS2 = approvalRepo.sweepExpiredForSession("s2", now = nowMs)
        assertEquals(1, sweptS2.size)
        assertEquals("req-s2-exp", sweptS2[0].id)
    }
}
