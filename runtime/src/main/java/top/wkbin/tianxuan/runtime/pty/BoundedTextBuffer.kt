package top.wkbin.tianxuan.runtime.pty

/**
 * 有界文本缓冲区（对齐 DSH terminal-bash/src/session.ts 的 BoundedTextBuffer）。
 *
 * 保留工作均摊在追加文本上，读取时组装保留的块。
 * 超限时从头驱逐，标记 [truncated]。
 *
 * @param maxBytes 最大保留 UTF-8 字节数
 * @param maxLines 最大保留逻辑行数；null 表示无行数上限
 */
class BoundedTextBuffer(
    private val maxBytes: Int = 4 * 1024 * 1024,
    private val maxLines: Int? = 10_000,
) {
    private class TextChunk(
        var text: String,
        var start: Int = 0,
        var next: TextChunk? = null,
    )

    private var head: TextChunk? = null
    private var tail: TextChunk? = null
    private var bytes = 0
    private var newlines = 0
    private var lastCodeUnit = 0
    private var dropped = false

    val truncated: Boolean get() = dropped
    val isEmpty: Boolean get() = head == null

    fun append(text: String) {
        if (text.isEmpty()) return
        bytes += text.toByteArray(Charsets.UTF_8).size
        val currentTail = tail
        if (currentTail != null) {
            val last = lastCodeUnit
            val first = text[0].code
            // 拼接可能把两个三字节代理项合成为一个四字节码点
            if (last in 0xd800..0xdbff && first in 0xdc00..0xdfff) bytes -= 2
        }
        for (index in text.indices) {
            if (text[index] == '\n') newlines++
        }
        lastCodeUnit = text[text.length - 1].code

        // 尾部合并不增长 head，驱逐不需要重扫增长的字符串
        if (currentTail != null && currentTail !== head
            && currentTail.text.length + text.length <= COALESCED_CHUNK_UNITS
        ) {
            currentTail.text += text
        } else {
            if (currentTail != null && currentTail.text.length <= COALESCED_CHUNK_UNITS) {
                // 复制已合并片段为一个字符串；大尾部已拥有自己的存储
                currentTail.text = currentTail.text
            }
            val chunk = TextChunk(text)
            if (currentTail == null) head = chunk else currentTail.next = chunk
            tail = chunk
        }

        // 超限时从头驱逐
        while (head != null && (bytes > maxBytes || (maxLines != null && newlines >= maxLines))) {
            val headChunk = head!!
            val first = headChunk.text[headChunk.start].code
            val second = if (headChunk.start + 1 < headChunk.text.length) {
                headChunk.text[headChunk.start + 1].code
            } else {
                headChunk.next?.text?.getOrNull(0)?.code ?: 0
            }
            val paired = first in 0xd800..0xdbff && second in 0xdc00..0xdfff
            bytes -= if (paired) 4 else if (first < 0x80) 1 else if (first < 0x800) 2 else 3
            if (first == 10) newlines--
            advance(if (paired) 2 else 1)
            dropped = true
        }

        // 驱逐过半后复制后缀，避免小尾巴保留大输入的后备存储
        val currentHead = head
        if (currentHead != null && currentHead.start >= currentHead.text.length / 2) {
            currentHead.text = currentHead.text.substring(currentHead.start)
            currentHead.start = 0
        }
    }

    private fun advance(units: Int) {
        var remaining = units
        while (remaining > 0 && head != null) {
            val headChunk = head!!
            val count = minOf(remaining, headChunk.text.length - headChunk.start)
            headChunk.start += count
            remaining -= count
            if (headChunk.start >= headChunk.text.length) head = headChunk.next
        }
        if (head == null) tail = null
    }

    /** 读取全部内容并清空。 */
    fun consume(): Pair<String, Boolean> {
        val snapshot = snapshot()
        head = null
        tail = null
        bytes = 0
        newlines = 0
        dropped = false
        return snapshot
    }

    /** 读取全部内容不清空。 */
    fun snapshot(): Pair<String, Boolean> {
        val chunks = mutableListOf<String>()
        var current = head
        while (current != null) {
            chunks.add(current.text.substring(current.start))
            current = current.next
        }
        return chunks.joinToString("") to dropped
    }

    companion object {
        private const val COALESCED_CHUNK_UNITS = 4096
    }
}
