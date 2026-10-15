package top.wkbin.tianxuan.harness.guard

import top.wkbin.tianxuan.harness.HarnessTool

/**
 * 工具调用超时强制策略（对齐 DSH guard/timeout-policy）。
 *
 * 每个工具可声明 timeoutMs，超时后结果替换为 TOOL_TIMEOUT 结构化错误。
 * 与现有 base 命令的 timeoutSeconds 正交——那是命令级超时，
 * 这是工具调用级超时（包含 LLM 调用、文件操作、网络请求等）。
 *
 * 错误码 [TOOL_TIMEOUT] 同时用于内部分类和结构化 error.code，
 * 嵌套外层超时不会被误读为本策略的超时。
 */
object ToolTimeoutPolicy {

    /** 工具调用超时错误码。 */
    const val TOOL_TIMEOUT = "TOOL_TIMEOUT"

    /**
     * 工具级超时配置（毫秒）。未在表中的工具使用默认值。
     *
     * 各值参考 DSH 同类工具的合理上限与天玄移动端实际延迟基线：
     * - read/edit/write：文件操作在沙箱内通常 <5s，超时给 30s
     * - base：命令执行最长 15min（已有 MAX_BASE_TIMEOUT_SECONDS 兜底）
     * - process：后台进程启动 <5s
     * - download：网络下载给 5min
     * - memory/plan/scratchpad：本地 DB 操作给 10s
     * - build_script：构建脚本给 5min
     * - invoke_subagent：子代理执行给 30min
     * - mcp：MCP 工具调用给 2min
     * - kb_search/kb_add/kb_list：知识库操作给 30s
     * - goal_*：目标管理给 10s
     * - batch：批量调用给 10min
     */
    private val TOOL_TIMEOUT_MS: Map<HarnessTool, Long> = mapOf(
        HarnessTool.READ to 30_000L,
        HarnessTool.WRITE to 30_000L,
        HarnessTool.EDIT to 30_000L,
        HarnessTool.BASE to 900_000L,       // 与 MAX_BASE_TIMEOUT_SECONDS 一致
        HarnessTool.PROCESS to 5_000L,
        HarnessTool.HOST to 60_000L,
        HarnessTool.DOWNLOAD to 300_000L,
        HarnessTool.MEMORY to 10_000L,
        HarnessTool.PLAN to 10_000L,
        HarnessTool.SCRATCHPAD to 10_000L,
        HarnessTool.HISTORY_SEARCH to 30_000L,
        HarnessTool.HISTORY_READ to 30_000L,
        HarnessTool.BUILD_SCRIPT to 300_000L,
        HarnessTool.SUBAGENT to 1_800_000L,
        HarnessTool.MCP to 120_000L,
        HarnessTool.LOAD_RULE to 10_000L,
        HarnessTool.LOAD_SKILL to 10_000L,
        HarnessTool.COMPRESS to 60_000L,
        HarnessTool.ASK_USER to 300_000L,
        HarnessTool.RENDER_SURFACE to 10_000L,
        HarnessTool.KNOWLEDGE to 30_000L,
        HarnessTool.KB_ADD to 30_000L,
        HarnessTool.KB_LIST to 30_000L,
        HarnessTool.GOAL_CREATE to 10_000L,
        HarnessTool.GOAL_PAUSE to 10_000L,
        HarnessTool.GOAL_RESUME to 10_000L,
        HarnessTool.GOAL_COMPLETE to 10_000L,
        HarnessTool.GOAL_BLOCK to 10_000L,
        HarnessTool.GOAL_STATUS to 10_000L,
        HarnessTool.GOAL_CLEAR to 10_000L,
        HarnessTool.BATCH to 600_000L,
    )

    /** 默认超时：未在表中的工具使用 60s。 */
    private const val DEFAULT_TIMEOUT_MS = 60_000L

    /**
     * 获取一个工具的调用超时（毫秒）。
     */
    fun timeoutFor(tool: HarnessTool): Long =
        TOOL_TIMEOUT_MS[tool] ?: DEFAULT_TIMEOUT_MS

    /**
     * 构建超时错误结果。
     */
    fun timeoutResult(tool: HarnessTool, timeoutMs: Long): Pair<Boolean, String> {
        val message = "tool call timed out after ${timeoutMs}ms"
        return false to "Error: $message\n\n【超时反思与纠错要求】\n$tool 调用超过 ${timeoutMs}ms 未返回。\n请检查：网络连接、文件大小、命令复杂度。\n可尝试：增大 timeoutMs（仅限 base/download）、拆分为更小操作、检查沙箱资源。"
    }

    /**
     * 检查一个工具的调用是否应在超时前被强制终止。
     * 当前实现仅提供超时值供调用方使用；实际强制终止在调用方实现。
     */
    fun shouldEnforce(tool: HarnessTool): Boolean = true
}
