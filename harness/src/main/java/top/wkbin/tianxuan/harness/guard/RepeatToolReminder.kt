package top.wkbin.tianxuan.harness.guard

import top.wkbin.tianxuan.harness.HarnessTool

/**
 * 重复工具调用提醒（对齐 DSH guard/repeat-tool-reminder）。
 *
 * 检测连续相同工具+相同参数的调用，在阈值处注入提醒消息，
 * 防止模型陷入死循环（反复调用同一工具同一参数）。
 *
 * 语义：
 * - include 模式匹配工具名；空表示追踪所有工具
 * - exclude 模式透明（不计数不重置）；匹配链键始终比较完整规范字符串
 * - 阈值触发提醒；达到阈值后重置计数器（每轮独立窗口）
 *
 * 参数规范化：工具参数 JSON 按 key 排序后比较，避免对象键序差异导致误报。
 *
 * @param thresholds 触发提醒的连续重复次数（默认 [3, 5, 8]）
 * @param include 追踪的工具名模式；空 = 全部
 * @param exclude 透明的工具名模式（不计数不重置）
 * @param argumentsPreviewChars 提醒中引用的参数预览上限（默认 500）
 */
class RepeatToolReminder(
    private val thresholds: IntArray = intArrayOf(3, 5, 8),
    private val include: List<String> = emptyList(),
    private val exclude: List<String> = emptyList(),
    private val argumentsPreviewChars: Int = 500,
) {
    /** 连续重复计数器状态（每个会话独立）。 */
    private class SessionState {
        /** 最近一次调用的规范化键（toolName + 规范化参数）。 */
        var lastKey: String? = null
        /** 当前连续重复次数。 */
        var repeatCount: Int = 0
    }

    private val sessions = mutableMapOf<String, SessionState>()

    /**
     * 检查一次工具调用是否触发重复提醒。
     *
     * @param sessionId 会话 ID
     * @param toolName 工具名
     * @param argsJson 工具参数 JSON 字符串
     * @return 需要注入提醒时返回提醒文本；否则返回 null
     */
    fun check(sessionId: String, tool: HarnessTool, argsJson: String): String? {
        val toolName = tool.name.lowercase()

        // 排除模式：透明通过（不计数不重置）
        if (exclude.isNotEmpty() && exclude.any { matchesPattern(toolName, it) }) {
            return null
        }

        // 包含模式：仅追踪匹配的工具
        if (include.isNotEmpty() && !include.any { matchesPattern(toolName, it) }) {
            return null
        }

        val state = sessions.getOrPut(sessionId) { SessionState() }
        val key = normalizeKey(toolName, argsJson)

        if (key == state.lastKey) {
            state.repeatCount++
            val threshold = thresholds.firstOrNull { it == state.repeatCount }
            if (threshold != null) {
                return buildReminder(toolName, state.repeatCount, argsJson)
            }
        } else {
            state.lastKey = key
            state.repeatCount = 1
        }

        return null
    }

    /**
     * 重置一个会话的重复计数（新轮次开始时调用）。
     */
    fun reset(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * 构建提醒文本。
     */
    private fun buildReminder(toolName: String, count: Int, argsJson: String): String {
        val preview = if (argsJson.length > argumentsPreviewChars) {
            argsJson.take(argumentsPreviewChars) + "…"
        } else {
            argsJson
        }
        return buildString {
            append("【重复调用提醒】")
            append("你已连续 $count 次调用 $toolName 且参数完全相同。")
            append("这很可能表示陷入了死循环或重复尝试相同操作。")
            append("\n\n参数预览：$preview")
            append("\n\n建议：")
            append("\n1. 检查上一次调用的输出结果，判断是否真的需要再次调用；")
            append("\n2. 若上次调用已返回结果，直接基于结果继续后续工作；")
            append("\n3. 若上次调用失败，修改参数或改用其他工具，而非重复相同调用；")
            append("\n4. 若确认需要重复调用（如轮询进度），请使用 process 工具的轮询模式或添加 sleep 间隔。")
        }
    }

    /**
     * 规范化工具参数为可比较的键。
     *
     * JSON 对象按键名排序后序列化，确保键序差异不影响比较。
     */
    private fun normalizeKey(toolName: String, argsJson: String): String {
        return "$toolName|${normalizeJson(argsJson)}"
    }

    /**
     * 规范化 JSON：按键名排序（简单实现——仅处理一层对象）。
     */
    private fun normalizeJson(json: String): String {
        return try {
            val parsed = kotlinx.serialization.json.Json.parseToJsonElement(json)
            when (parsed) {
                is kotlinx.serialization.json.JsonObject -> {
                    val sorted = parsed.toSortedMap()
                    sorted.entries.joinToString(",") { (k, v) -> "$k:$v" }
                }
                else -> json
            }
        } catch (_: Exception) {
            json
        }
    }

    /**
     * 简单模式匹配：支持 * 通配符。
     */
    private fun matchesPattern(toolName: String, pattern: String): Boolean {
        if (pattern == "*") return true
        if (pattern.endsWith("*")) {
            val prefix = pattern.removeSuffix("*")
            return toolName.startsWith(prefix)
        }
        return toolName == pattern
    }
}
