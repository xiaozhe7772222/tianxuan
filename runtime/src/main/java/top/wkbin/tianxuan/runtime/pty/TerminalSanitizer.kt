package top.wkbin.tianxuan.runtime.pty

/**
 * 流式终端控制序列净化器（对齐 DSH terminal-bash/src/sanitize.ts 的 TerminalSanitizer）。
 *
 * 逐块消费 PTY 输出数据，剥离 CSI/OSC/短转义序列，保留普通行文本与
 * 私有 prompt 标记（OSC `133;D;`）。跨块的未完成序列通过 carry 缓冲保留。
 *
 * 全终端模拟故意推迟——行输出与 prompt 标记是支持的契约。
 *
 * @param maxPendingBytes 未完成序列缓冲的字节上限，超限丢弃。
 */
class TerminalSanitizer(
    private val maxPendingBytes: Int = 8192,
) {
    private var pending = ""
    private var discardMode: DiscardMode? = null
    private var discardOscEscape = false
    private var trailingCarriageReturn = false
    private var trackingPromptTail = false

    private enum class DiscardMode { OSC, CSI }

    /** 一个净化后的数据块，附带是否包含 prompt 标记。 */
    data class SanitizedChunk(
        val text: String,
        val prompt: Boolean,
        /** 标记后的可打印文本尾部（仅在追踪中时存在）。 */
        val promptTail: String? = null,
    )

    /**
     * 消费一个解码后的 PTY 数据块。
     * @return 净化后的可打印文本、是否包含 prompt 标记、以及标记后的提示语尾部。
     */
    fun push(chunk: String): SanitizedChunk {
        pending += discardPrefix(chunk)
        val textBuilder = StringBuilder()
        var prompt = false
        var includePromptTail = trackingPromptTail
        var promptTail = StringBuilder()
        var index = 0

        fun appendText(value: String) {
            textBuilder.append(value)
            if (trackingPromptTail) promptTail.append(value)
        }

        while (index < pending.length) {
            val escape = pending.indexOf('\u001b', index)
            if (escape < 0) {
                appendText(pending.substring(index))
                index = pending.length
                break
            }
            appendText(pending.substring(index, escape))
            if (escape + 1 >= pending.length) {
                index = escape
                break
            }
            when (val kind = pending[escape + 1]) {
                ']' -> {
                    // OSC sequence: ESC ] ... BEL or ESC ] ... ST
                    val bel = pending.indexOf('\u0007', escape + 2)
                    val st = pending.indexOf("\u001b\\", escape + 2)
                    val end = when {
                        bel >= 0 && st >= 0 -> minOf(bel + 1, st + 2)
                        bel >= 0 -> bel + 1
                        st >= 0 -> st + 2
                        else -> -1
                    }
                    if (end < 0) {
                        index = escape
                        break
                    }
                    val terminatorBytes = if (pending[end - 1] == '\u0007') 1 else 2
                    val content = pending.substring(escape + 2, end - terminatorBytes)
                    if (content.startsWith(PROMPT_MARKER_PREFIX)) {
                        prompt = true
                        trackingPromptTail = true
                        includePromptTail = true
                        promptTail = StringBuilder()
                    }
                    index = end
                }
                '[' -> {
                    // CSI sequence: ESC [ ... final byte (0x40..0x7e)
                    var end = escape + 2
                    while (end < pending.length) {
                        val code = pending[end].code
                        if (code in 0x40..0x7e) break
                        end++
                    }
                    if (end >= pending.length) {
                        index = escape
                        break
                    }
                    index = end + 1
                }
                else -> {
                    // Two-byte escape (save/restore cursor etc.)
                    index = escape + 2
                }
            }
        }
        pending = pending.substring(index)
        enforcePendingBound()
        val normalizedText = normalizeText(textBuilder.toString())
        return SanitizedChunk(
            text = normalizedText,
            prompt = prompt,
            promptTail = if (includePromptTail) promptTail.toString() else null,
        )
    }

    /**
     * PTY 退出时冲刷尾部可打印片段。
     * @return 剩余可打印文本；未完成转义序列丢弃。
     */
    fun flush(): String {
        val text = if (pending.startsWith("\u001b")) "" else pending
        pending = ""
        discardMode = null
        discardOscEscape = false
        trackingPromptTail = false
        val normalized = normalizeText(text)
        return if (!trailingCarriageReturn) {
            normalized
        } else {
            trailingCarriageReturn = false
            "$normalized\n"
        }
    }

    private fun normalizeText(text: String): String {
        var complete = if (trailingCarriageReturn) "\r$text" else text
        trailingCarriageReturn = false
        if (complete.endsWith('\r')) {
            complete = complete.dropLast(1)
            trailingCarriageReturn = true
        }
        return normalizeTerminalText(complete)
    }

    private fun enforcePendingBound() {
        if (pending.toByteArray(Charsets.UTF_8).size <= maxPendingBytes) return
        discardMode = if (pending.isNotEmpty() && pending[1] == ']') DiscardMode.OSC else DiscardMode.CSI
        pending = ""
    }

    private fun discardPrefix(chunk: String): String {
        val mode = discardMode ?: return chunk
        if (mode == DiscardMode.CSI) {
            for (index in chunk.indices) {
                val code = chunk[index].code
                if (code in 0x40..0x7e) {
                    discardMode = null
                    return chunk.substring(index + 1)
                }
            }
            return ""
        }

        // OSC discard
        var index = 0
        if (discardOscEscape) {
            discardOscEscape = false
            if (chunk.startsWith("\\")) {
                discardMode = null
                return chunk.substring(1)
            }
        }
        while (index < chunk.length) {
            if (chunk[index] == '\u0007') {
                discardMode = null
                return chunk.substring(index + 1)
            }
            if (chunk[index] == '\u001b') {
                if (index + 1 < chunk.length && chunk[index + 1] == '\\') {
                    discardMode = null
                    return chunk.substring(index + 2)
                }
                if (index + 1 == chunk.length) discardOscEscape = true
            }
            index++
        }
        return ""
    }
}

/**
 * 规范化 CRLF 和独立回车，移除 BEL。
 */
fun normalizeTerminalText(text: String): String =
    text.replace("\r\n", "\n").replace("\r", "\n").replace("\u0007", "")
