package top.wkbin.tianxuan.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.wkbin.tianxuan.core.model.CcAgentState
import top.wkbin.tianxuan.core.model.CcAgentType
import top.wkbin.tianxuan.core.model.CcProviderProfile
import top.wkbin.tianxuan.core.model.CcSwitchDaemonStatus
import java.io.IOException
import java.util.concurrent.TimeUnit

class CcSwitchClient(
    httpClientProvider: HttpClientProvider,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client: OkHttpClient = httpClientProvider.create().newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getStatus(port: Int = 19870): Result<CcSwitchDaemonStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/api/status")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                json.decodeFromString<CcSwitchDaemonStatus>(body)
            }
        }
    }

    suspend fun getAgents(port: Int = 19870): Result<List<CcAgentState>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/api/agents")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                json.decodeFromString<List<CcAgentState>>(body)
            }
        }
    }

    suspend fun getProviders(port: Int = 19870): Result<List<CcProviderProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/api/providers")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body.string()
                json.decodeFromString<List<CcProviderProfile>>(body)
            }
        }
    }

    suspend fun switchAgentProvider(
        agentId: String,
        providerId: String,
        port: Int = 19870,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            // agentId/providerId 均外部可注入：路径段用 HttpUrl 编码，JSON 用结构化序列化，
            // 不再手工拼 URL/JSON 字符串（避免 agentId 含 / 或 ? 时越权访问其他端点）
            val url = okhttp3.HttpUrl.Builder()
                .scheme("http")
                .host("127.0.0.1")
                .port(port)
                .addPathSegment("api")
                .addPathSegment("agents")
                .addPathSegment(agentId)
                .addPathSegment("switch")
                .build()
            val payload = org.json.JSONObject().put("providerId", providerId).toString()
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Switch failed: HTTP ${response.code}")
                }
                true
            }
        }
    }

    suspend fun saveProvider(
        profile: CcProviderProfile,
        port: Int = 19870,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(CcProviderProfile.serializer(), profile)
            val request = Request.Builder()
                .url("http://127.0.0.1:$port/api/providers")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Save provider failed: HTTP ${response.code}")
                }
                true
            }
        }
    }

    suspend fun installOrUpdateAgent(
        agentId: String,
        version: String? = null,
        port: Int = 19870,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            // 同 switchAgentProvider：agentId 走路径段编码，version 走结构化 JSON，
            // 避免含 / ? 或引号的 id 越权访问其他端点 / 破坏 JSON 结构
            val url = okhttp3.HttpUrl.Builder()
                .scheme("http")
                .host("127.0.0.1")
                .port(port)
                .addPathSegment("api")
                .addPathSegment("agents")
                .addPathSegment(agentId)
                .addPathSegment("install")
                .build()
            val payload = if (version != null) {
                org.json.JSONObject().put("version", version).toString()
            } else {
                "{}"
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Install agent failed: HTTP ${response.code}")
                }
                true
            }
        }
    }
}
