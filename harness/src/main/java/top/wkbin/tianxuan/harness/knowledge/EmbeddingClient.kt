package top.wkbin.tianxuan.harness.knowledge

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 调用 OpenAI 兼容的 /embeddings 端点，把文本转成向量。
 *
 * 复用 chat 通路的 [OkHttpClient] 与 baseUrl/apiKey（来自 [top.wkbin.tianxuan.core.tools.ProviderRepository]），
 * 不引入额外依赖。
 */
class EmbeddingClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    @Serializable
    private data class EmbeddingResponse(val data: List<EmbeddingItem>)

    @Serializable
    private data class EmbeddingItem(val embedding: List<Float>)

    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    suspend fun embed(
        texts: List<String>,
        baseUrl: String,
        apiKey: String?,
        model: String = DEFAULT_MODEL,
        endpointSuffix: String = "/embeddings",
    ): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("input", buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val endpoint = baseUrl.trimEnd('/') + endpointSuffix.trimStart('/').let {
            if (it.endsWith(endpointSuffix.trimStart('/'))) it else "${baseUrl.trimEnd('/')}${endpointSuffix}"
        }
        val request = Request.Builder()
            .url(endpoint)
            .apply { apiKey?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(requestBody)
            .build()

        val call = okHttpClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    val maskedEndpoint = endpoint.substringBefore('/').take(40)
                    if (response.code == 404) {
                        throw IOException(
                            "嵌入端点 404 NOT_FOUND（端点: $maskedEndpoint，模型: $model）：" +
                                "当前 Base URL 可能不提供 $endpointSuffix 端点，或嵌入模型名 \"$model\" 不可用。" +
                                "请在知识库页设置「嵌入模型名」与「嵌入端点后缀」以匹配你的提供商。响应: ${body.take(300)}"
                        )
                    }
                    if (response.code == 429) {
                        throw IOException("嵌入接口限流（429）：请稍后重试或降低添加频率（端点: $maskedEndpoint）")
                    }
                    throw IOException("嵌入接口错误 HTTP ${response.code}（端点: $maskedEndpoint，模型: $model）：${body.take(500)}")
                }
                val body = response.body.string()
                val parsed = runCatching { json.decodeFromString(EmbeddingResponse.serializer(), body) }
                    .getOrElse { parseEmbeddingsManually(body) }
                parsed.data.map { item -> FloatArray(item.embedding.size) { idx -> item.embedding[idx] } }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: java.net.SocketException) {
            val maskedEndpoint = endpoint.substringBefore('/').take(40)
            throw IOException(
                "嵌入连接被关闭（Socket closed）：请检查激活模型的 Base URL 是否可访问、网络是否受限、以及 API Key 是否有效（端点: $maskedEndpoint）",
                e,
            )
        } catch (e: java.net.UnknownHostException) {
            throw IOException("嵌入连接失败：无法解析主机（请检查 Base URL 拼写与设备网络）", e)
        } finally {
            cancelHandle?.dispose()
        }
    }

    /** 备用解析：某些 provider 返回非标准结构时降级到直接取 data[].embedding。 */
    private fun parseEmbeddingsManually(body: String): EmbeddingResponse {
        val obj = json.parseToJsonElement(body).jsonObject
        val data = obj["data"]?.jsonArray ?: throw IOException("Embedding response missing 'data' field")
        val items = data.map { entry ->
            val embedding = entry.jsonObject["embedding"]?.jsonArray
                ?: throw IOException("Embedding item missing 'embedding' field")
            EmbeddingItem(embedding.map { it.jsonPrimitive.content.toFloat() })
        }
        return EmbeddingResponse(items)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val DEFAULT_MODEL = "text-embedding-3-small"
        const val DEFAULT_ENDPOINT_SUFFIX = "/embeddings"
    }
}
