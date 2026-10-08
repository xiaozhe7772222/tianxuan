package top.wkbin.tianxuan.harness.knowledge

/**
 * 简单文本分块：按段落切分，单块不超过 [maxChars]（约 200 token）。
 * 段落过短则合并；过长则按 [maxChars] 硬切。
 */
object TextChunker {

    fun chunk(
        text: String,
        maxChars: Int = 800,
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text.split(Regex("\n{2,}"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isEmpty()) continue
            if (current.isNotEmpty() && current.length + trimmed.length > maxChars) {
                chunks += current.toString().trim()
                current.setLength(0)
            }
            if (trimmed.length > maxChars) {
                if (current.isNotEmpty()) {
                    chunks += current.toString().trim()
                    current.setLength(0)
                }
                var i = 0
                while (i < trimmed.length) {
                    val end = (i + maxChars).coerceAtMost(trimmed.length)
                    chunks += trimmed.substring(i, end).trim()
                    i = end
                }
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(trimmed)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks
    }
}
