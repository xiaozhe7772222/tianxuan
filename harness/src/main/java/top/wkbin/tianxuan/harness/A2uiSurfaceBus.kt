package top.wkbin.tianxuan.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * render_surface（A2UI PoC）工具的界面载荷总线。
 *
 * A2UI = Agent-to-UI：智能体不写代码，而是输出 JSON Lines 协议消息描述界面，
 * 客户端用 Compose 渲染器（feature:a2uipoc 模块）映射为原生组件。安全边界来自
 * Component Catalog：智能体只能使用目录里声明的组件，与工具白名单同构。
 *
 * PoC 简化说明：总线用 object 单例而非 Koin 注入，避免给 ToolExecutor/KoinModule
 * 两个棘轮基线文件增行；转正式实现时应改为接口 + 注入，并把总线收敛进会话生命周期。
 *
 * 深度校验走钩子注入（[installDeepValidator]）：官方 A2UI parser 只允许在
 * feature:a2uipoc 使用（architecture-policy），harness 保持零 a2ui 依赖，
 * 由 a2uipoc 启动时注册基于官方 parser 的真校验，失败原因回传给模型自我纠正。
 */
object A2uiSurfaceBus {

    /** 工具产出的一条界面载荷（一次 render_surface 调用 = 一个 surface 的协议消息集）。 */
    data class A2uiSurfacePayload(
        val surfaceId: String,
        val title: String,
        /** A2UI 协议消息数组的 JSON 字符串（已归一化，渲染器逐条 processInput）。 */
        val messagesJson: String,
        val messageCount: Int,
        /** 发起本次 render_surface 的会话 id；用户交互事件按它路由回原会话。 */
        val sessionId: String,
        val createdAt: Long,
    )

    /** 用户与 A2UI 界面交互产生的事件（由渲染器从官方 outboundEvents 转发而来）。 */
    data class A2uiUserEvent(
        val surfaceId: String,
        val surfaceTitle: String,
        val sessionId: String,
        val componentId: String,
        val eventName: String,
        val context: Map<String, Any?>,
        val timestamp: Long,
    )

    /** A2UI 引擎运行时错误（组件被目录校验拒绝、surface 状态异常等），同样回传给智能体。 */
    data class A2uiErrorEvent(
        val surfaceId: String,
        val surfaceTitle: String,
        val sessionId: String,
        val code: String,
        val message: String,
    )

    private val _surfaces = MutableStateFlow<List<A2uiSurfacePayload>>(emptyList())

    /** 最近发布的界面载荷（新的在前，最多 [MAX_CACHED_SURFACES] 条，同 surfaceId 覆盖旧条目）。 */
    val surfaces: StateFlow<List<A2uiSurfacePayload>> = _surfaces.asStateFlow()

    /** 引擎出站消息出口（用户交互/运行时错误）；由装配层（ChatViewModel）注册，路由回原会话。 */
    @Volatile
    var userEventSink: ((A2uiUserEvent) -> Unit)? = null

    @Volatile
    var errorEventSink: ((A2uiErrorEvent) -> Unit)? = null

    @Volatile
    private var deepValidator: ((String) -> String?)? = null

    /**
     * 注册深度校验器（a2uipoc 启动时调用）：入参为归一化后的 messages JSON，
     * 返回 null 表示受理通过，返回非 null 文案作为 render_surface 的失败原因回传给模型。
     */
    fun installDeepValidator(validator: ((String) -> String?)?) {
        deepValidator = validator
    }

    fun clear() {
        _surfaces.value = emptyList()
    }

    fun findSurface(surfaceId: String): A2uiSurfacePayload? =
        _surfaces.value.firstOrNull { it.surfaceId == surfaceId }

    /**
     * render_surface 工具执行入口：校验参数 → 登记载荷 → 返回给模型的结果文本。
     * 返回值约定与 ToolExecutor 一致（success to output）。
     */
    fun publishFromTool(args: JsonObject, sessionId: String = ""): Pair<Boolean, String> {
        val payload = parseToolArgs(args, sessionId).getOrElse { failure ->
            return false to "render_surface 参数校验未通过：${failure.message}。请修正参数后重新调用。"
        }
        publish(payload)
        return true to buildString {
            append("已提交 A2UI 界面「${payload.title}」（surfaceId=${payload.surfaceId}，")
            append("${payload.messageCount} 条协议消息）。界面已在用户聊天流中渲染为原生组件；")
            append("如需更新该界面，请用相同 surfaceId 再次调用并只携带 updateComponents 消息；")
            append("如需移除界面，发送 deleteSurface 消息。用户与界面的交互（按钮点击等）")
            append("会以 [A2UI 界面事件] 消息回传给你。")
        }
    }

    private fun publish(payload: A2uiSurfacePayload) {
        _surfaces.value = (listOf(payload) + _surfaces.value.filterNot { it.surfaceId == payload.surfaceId })
            .take(MAX_CACHED_SURFACES)
    }

    /** 渲染器转发引擎出站消息的入口（用户交互 → userEventSink，运行时错误 → errorEventSink）。 */
    fun publishUserEvent(event: A2uiUserEvent) {
        userEventSink?.invoke(event)
    }

    fun publishErrorEvent(event: A2uiErrorEvent) {
        errorEventSink?.invoke(event)
    }

    /** 把用户交互事件格式化为注入 agent 会话的消息文本。 */
    fun formatUserEvent(event: A2uiUserEvent): String = buildString {
        append("[A2UI 界面事件] 用户在界面「${event.surfaceTitle}」（surfaceId=${event.surfaceId}）")
        append("上触发了交互：组件 componentId=${event.componentId}，事件 eventName=${event.eventName}")
        if (event.context.isNotEmpty()) append("，携带 context=${event.context}")
        append("。这是 A2UI 界面的用户交互回传，请根据事件语义继续处理；")
        append("如需更新界面，用相同 surfaceId 调用 render_surface 并只携带 updateComponents 消息。")
    }

    /** 把引擎运行时错误格式化为注入 agent 会话的消息文本（否则界面只会静默转圈）。 */
    fun formatErrorEvent(event: A2uiErrorEvent): String = buildString {
        append("[A2UI 界面错误] 界面「${event.surfaceTitle}」（surfaceId=${event.surfaceId}）")
        append("渲染失败：[${event.code}] ${event.message}。")
        append("请修正组件定义（属性名/类型/子组件引用必须符合 Catalog），")
        append("用相同 surfaceId 重新调用 render_surface 并携带完整 components 列表（含 id=root 的顶部组件）。")
    }

    internal fun parseToolArgs(args: JsonObject, sessionId: String = ""): Result<A2uiSurfacePayload> = runCatching {
        val surfaceId = args["surfaceId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(surfaceId.isNotEmpty()) { "缺少 surfaceId" }
        require(surfaceId.length <= MAX_ID_CHARS && SURFACE_ID_REGEX.matches(surfaceId)) {
            "surfaceId 仅允许字母数字与 _ . : -，且不超过 $MAX_ID_CHARS 字符"
        }
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim().takeIf { !it.isNullOrBlank() } ?: "A2UI 界面"
        require(title.length <= MAX_TITLE_CHARS) { "title 不超过 $MAX_TITLE_CHARS 字符" }
        val rawMessages = args["messages"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(rawMessages.length in MIN_JSON_CHARS..MAX_JSON_CHARS) {
            "messages 必须是 A2UI 协议消息数组的 JSON 字符串（长度 $MIN_JSON_CHARS..$MAX_JSON_CHARS）"
        }
        val messagesJson = parseMessagesArray(rawMessages).toString()
        deepValidator?.invoke(messagesJson)?.let { error -> error("A2UI 协议校验失败：$error") }
        A2uiSurfacePayload(
            surfaceId = surfaceId,
            title = title,
            messagesJson = messagesJson,
            messageCount = Json.parseToJsonElement(messagesJson).let { it as JsonArray }.size,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * 把模型提交的 messages 原文归一化为协议消息数组的紧凑 JSON；解析失败返回 null。
     * 工具路径（校验）与聊天卡片路径（渲染）共用，保证两条路律试到同一份归一化载荷。
     */
    fun normalizeMessagesJson(raw: String): String? = runCatching {
        parseMessagesArray(raw).toString()
    }.getOrNull()

    /**
     * 解析 messages 原文为协议消息数组，附带对模型常见双转义错误的自动修复：
     * 1) 正常路径：直接是 JSON 数组；
     * 2) 整体被包成 JSON 字符串（双重编码）→ 剥一层再解析；
     * 3) 引号带多余反斜杠转义（[{\"version\"…）→ 去掉多余转义再解析。
     */
    private fun parseMessagesArray(raw: String): JsonArray {
        val direct = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        if (direct is JsonArray) return validateArrayShape(direct)
        if (direct is JsonPrimitive && direct.isString) {
            val unwrapped = runCatching { Json.parseToJsonElement(direct.content) }.getOrNull()
            if (unwrapped is JsonArray) return validateArrayShape(unwrapped)
        }
        if (raw.contains("\\\"")) {
            val repaired = runCatching { Json.parseToJsonElement(raw.replace("\\\"", "\"")) }.getOrNull()
            if (repaired is JsonArray) return validateArrayShape(repaired)
        }
        throw IllegalArgumentException(
            "messages 必须是 A2UI 协议消息数组的 JSON 字符串（首条 createSurface，" +
                "其后 updateComponents/deleteSurface）；不要对 messages 参数做二次转义，" +
                "直接给出数组原文即可",
        )
    }

    private fun validateArrayShape(array: JsonArray): JsonArray {
        require(array.isNotEmpty() && array.all { it is JsonObject && it.containsKey("version") }) {
            "messages 数组每项必须是带 version 的 A2UI 协议消息对象"
        }
        array.forEach { message ->
            val version = (message as JsonObject)["version"]?.jsonPrimitive?.contentOrNull
            require(version in SUPPORTED_VERSIONS) {
                "协议 version 仅支持 $SUPPORTED_VERSIONS（收到：$version）"
            }
        }
        return array
    }

    /** 供 feature:a2uipoc 演示屏使用的内置示例（与 [A2uiSurfaceContract.CATALOG_ID] 对齐）。 */
    fun sampleMessagesJson(surfaceId: String = "demo-1"): String = buildSampleMessages(surfaceId)

    internal const val MAX_ID_CHARS = 64
    internal const val MAX_TITLE_CHARS = 80
    internal const val MIN_JSON_CHARS = 8
    internal const val MAX_JSON_CHARS = 200_000
    internal const val MAX_CACHED_SURFACES = 8
    internal val SURFACE_ID_REGEX = Regex("[A-Za-z0-9_.:-]+")

    /** 与官方 A2uiJsonMessageParser.SUPPORTED_VERSIONS 对齐，库升级时需联动核对。 */
    internal val SUPPORTED_VERSIONS = listOf("v0.9", "v0.9.1")

    // 注意：含模板引用，不能声明为 const val（棘轮文件外的新文件，保持 private fun 即可）
    private fun buildSampleMessages(surfaceId: String): String =
        """[{"version":"v0.9","createSurface":{"surfaceId":"$surfaceId","catalogId":"${A2uiSurfaceContract.CATALOG_ID}"}},
{"version":"v0.9","updateComponents":{"surfaceId":"$surfaceId","components":[
{"id":"root","component":"Column","children":["title","card","hint"]},
{"id":"title","component":"Text","text":"天玄 A2UI PoC"},
{"id":"card","component":"Card","child":"cardText"},
{"id":"cardText","component":"Text","text":"这段界面由智能体通过 render_surface 生成，渲染为原生 Compose 组件。"},
{"id":"hint","component":"Text","text":"组件范围由 Catalog 声明，智能体无法执行任意代码。"}
]}}]"""
}
