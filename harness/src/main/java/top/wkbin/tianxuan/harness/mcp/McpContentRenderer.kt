package top.wkbin.tianxuan.harness.mcp

import java.util.Base64

/**
 * MCP content block 渲染（对齐 DeepSeek Harness mcp-client/tools.ts 的 content projection）。
 *
 * - text block → 原文
 * - image block（data 为 base64 + mimeType）→ 解码验证后渲染为 `[图片: <mimeType> N 字节]` 引用
 * - 其他类型 → 类型标注
 *
 * 此前只取 `it.text.orEmpty()`，图片 block 的输出会变成空字符串再 ifBlank 成"执行成功"，
 * 模型拿到无意义结果。现在图片以引用形式返回（模型可通过视觉通道处理或提示用户查看）。
 */
internal fun formatContentItem(item: top.wkbin.tianxuan.harness.mcp.McpContentItem): String = when {
    item.type == "text" -> item.text.orEmpty()
    item.type == "image" && item.data != null -> {
        val bytes = try {
            Base64.getDecoder().decode(item.data)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (bytes == null) {
            "[图片: base64 解码失败]"
        } else {
            "[图片: ${item.mimeType ?: "application/octet-stream"} ${bytes.size} 字节]"
        }
    }
    else -> "[${item.type}: ${item.text.orEmpty().take(200)}]"
}
