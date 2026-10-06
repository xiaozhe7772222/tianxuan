package top.wkbin.tianxuan.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.tianxuan.core.model.CcAgentState
import top.wkbin.tianxuan.core.model.CcAgentType
import top.wkbin.tianxuan.core.model.CcProviderProfile
import top.wkbin.tianxuan.core.model.CcSwitchDaemonStatus

class CcSwitchClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testOfflineConnectionGracefulFailure() = runBlocking {
        val client = CcSwitchClient(HttpClientProvider())
        // Port 19871 is not bound, should fail gracefully without throwing uncaught exceptions
        val statusRes = client.getStatus(port = 19871)
        assertTrue(statusRes.isFailure)

        val agentsRes = client.getAgents(port = 19871)
        assertTrue(agentsRes.isFailure)

        val providersRes = client.getProviders(port = 19871)
        assertTrue(providersRes.isFailure)
    }

    @Test
    fun testSerializationOfModels() {
        val status = CcSwitchDaemonStatus(
            running = true,
            port = 19870,
            version = "1.0.0",
            proxyEnabled = true,
        )
        val serialized = json.encodeToString(CcSwitchDaemonStatus.serializer(), status)
        val deserialized = json.decodeFromString<CcSwitchDaemonStatus>(serialized)
        assertEquals(19870, deserialized.port)
        assertTrue(deserialized.running)

        val agent = CcAgentState(
            type = CcAgentType.CLAUDE_CODE,
            installed = true,
            currentVersion = "1.0.0",
            activeProviderName = "DeepSeek",
        )
        val agentStr = json.encodeToString(CcAgentState.serializer(), agent)
        val agentBack = json.decodeFromString<CcAgentState>(agentStr)
        assertEquals(CcAgentType.CLAUDE_CODE, agentBack.type)
        assertEquals("DeepSeek", agentBack.activeProviderName)

        val provider = CcProviderProfile(
            id = "deepseek",
            name = "DeepSeek",
            protocol = "OPENAI",
            baseUrl = "https://api.deepseek.com",
            selectedModel = "deepseek-chat",
        )
        val provStr = json.encodeToString(CcProviderProfile.serializer(), provider)
        val provBack = json.decodeFromString<CcProviderProfile>(provStr)
        assertEquals("deepseek", provBack.id)
        assertEquals("https://api.deepseek.com", provBack.baseUrl)
    }
}
