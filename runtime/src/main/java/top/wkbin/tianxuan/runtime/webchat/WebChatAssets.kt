package top.wkbin.tianxuan.runtime.webchat

/**
 * 局域网页静态资源的扩展名判定与 MIME 映射。
 *
 * 独立成文件的理由与 [PinVerifier] 相同：可单测，且不与 [WebChatBridgeServer] 的
 * 行数棘轮冲突。
 *
 * 一张表承担两个职责是有意的：**返回 null 同时表示「不是已知静态资源」**。
 * 静态资源命中与否必须与 MIME 判定用同一份扩展名清单——若分成两份
 * （一份判路由、一份给类型），新增扩展名时极易只改一处，出现
 * 「按资源取文件却回落到 index.html」或「回落到 index.html 却返回 image/png」
 * 这类只在特定路径下暴露的错配。
 */
internal object WebChatAssets {

    /** 未知类型资源的兜底 MIME。 */
    const val DEFAULT_MIME = "application/octet-stream"

    /**
     * 按扩展名给出 MIME；返回 null 表示「未知扩展名」，调用方据此走前端路由回落。
     *
     * 空路径同样返回 null：根路径 `/` 是前端入口，不是资源。
     */
    fun mimeTypeOrNull(path: String): String? {
        if (path.isBlank()) return null
        return when {
            path.endsWith(".html") -> "text/html; charset=utf-8"
            path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript; charset=utf-8"
            path.endsWith(".css") -> "text/css; charset=utf-8"
            path.endsWith(".json") -> "application/json; charset=utf-8"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".png") -> "image/png"
            else -> null
        }
    }
}
