package top.wkbin.tianxuan.harness.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/** Latest MCP protocol version implemented by this client. It is a spec identifier, not today's date. */
internal const val MCP_PROTOCOL_VERSION = "2025-06-18"

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
)

/**
 * JSON-RPC 2.0 规范允许 id 为字符串、数字或 null。
 * 某些 MCP server 会把请求的字符串 id 解析为数字后原样返回，
 * 导致 [JsonRpcResponse] 反序列化失败（String 字段遇到数字 id 抛 JsonDecodingException），
 * 帧被静默丢弃，等待方挂满超时。
 *
 * 此 serializer 在反序列化前把非字符串 id 归一化为字符串，
 * 保证下游 `it.id == requestId` 比较始终成立。
 *
 * 不使用 @Serializable(with=...) 注解：那会让 JsonRpcResponse.serializer() 返回本 serializer 自身，
 * 构成循环引用。解码点需显式传入 [JsonRpcResponseSerializer]。
 */
object JsonRpcResponseSerializer :
    JsonTransformingSerializer<JsonRpcResponse>(JsonRpcResponse.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val id = element["id"] ?: return element
        // 只把数字 id 归一化为字符串；id:null（JsonNull）必须保持原样，
        // 否则熔断帧/无 id 错误帧的 parsed.id 会从 null 变成 "null"，路由失效。
        if (id is JsonPrimitive && !id.isString && id !is kotlinx.serialization.json.JsonNull) {
            return JsonObject(element.toMutableMap().apply {
                put("id", JsonPrimitive(id.content))
            })
        }
        return element
    }
}

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class McpInitializeParams(
    val protocolVersion: String = MCP_PROTOCOL_VERSION,
    val capabilities: JsonObject = JsonObject(emptyMap()),
    val clientInfo: McpClientInfo = McpClientInfo("TianXuan-Agent", "1.0.0"),
)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
)

@Serializable
data class McpClientInfo(
    val name: String,
    val version: String,
)

@Serializable
data class McpToolDto(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class McpToolsListResponse(
    val tools: List<McpToolDto> = emptyList(),
)

@Serializable
data class McpCallToolParams(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class McpContentItem(
    val type: String = "text",
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContentItem> = emptyList(),
    val isError: Boolean = false,
)
