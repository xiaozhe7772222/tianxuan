package top.wkbin.tianxuan.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * render_surface 工具对 LLM 暴露的契约（名称 / 描述 / 参数 Schema / ApiToolDefinition）。
 *
 * 单独成文件的原因：ProviderClient 的 TOOLS 列表在尺寸棘轮基线内（只许缩减），
 * 这里把定义收敛为一个常量，ProviderClient 只需追加一行引用。
 */
object A2uiSurfaceContract {

    const val TOOL_NAME = "render_surface"

    /**
     * M3 Basic Catalog 的目录 id：createSurface 消息里的 catalogId 必须与之一致。
     * 值与 androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.CatalogId 对齐
     * （harness 不依赖该库，故此处以常量同步；库升级时需联动核对）。
     */
    const val CATALOG_ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

    private const val TOOL_DESCRIPTION =
        "把结构化数据渲染为设备上的原生交互界面（A2UI 协议 PoC）。内容适合可视化（对比/排行/" +
            "表单/看板）时用本工具提交，然后一两句话总结，不要在正文重复数据。" +
            "messages 是 A2UI 消息数组的 JSON 字符串：首条 createSurface（新界面用全新 surfaceId），" +
            "其后 updateComponents（扁平 components，容器用 children/child 引用子组件 id，恰一个 root），" +
            "deleteSurface 移除界面。version 固定 v0.9；禁止对 messages 二次转义；" +
            "文本写明文常量不用表达式，不得杜撰组件类型（无表格组件，表格用 List 或 Row/Column 组合）。" +
            "用户点击界面等交互会以 [A2UI 界面事件] 消息回传，据此处理或用相同 surfaceId 更新界面。"

    private const val TOOL_SCHEMA_JSON =
        """{"type":"object","properties":{"surfaceId":{"type":"string","description":"界面唯一 id（字母数字与 _ . : -，≤64 字符）；更新已有界面时必须复用原 id"},"title":{"type":"string","description":"界面短标题（≤80 字符）"},"messages":{"type":"string","description":"A2UI 协议消息数组的 JSON 字符串；不要二次转义，version 固定为 v0.9，catalogId 固定为 $CATALOG_ID。示例：[{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s1\",\"catalogId\":\"$CATALOG_ID\"}},{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"t\"]},{\"id\":\"t\",\"component\":\"Text\",\"text\":\"你好\"}]}}]"}},"required":["surfaceId","messages"]}"""

    /** ProviderClient.TOOLS 直接引用，避免在棘轮基线文件里展开多行定义。 */
    val TOOL_DEFINITION: ApiToolDefinition = ApiToolDefinition(
        function = ApiFunctionDefinition(
            name = TOOL_NAME,
            description = TOOL_DESCRIPTION,
            parameters = Json.parseToJsonElement(TOOL_SCHEMA_JSON) as JsonObject,
        ),
    )
}
