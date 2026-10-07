package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.BuiltinMcpPresets
import top.wkbin.tianxuan.core.model.McpToolInfo
import top.wkbin.tianxuan.harness.mcp.McpToolApiName
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive


data class ApprovalDecision(
    val required: Boolean,
    val riskLevel: String = "low",
    val reason: String = "",
    val summary: String = "",
)

/** Host-side policy. The model prompt is deliberately not part of this decision. */
class ApprovalPolicyEngine(
    private val pathResolver: HarnessPathResolver,
) {
    /**
     * 内置 browser server 在工具 API 名里可能出现的 server 段集合：
     * 编码名 `mcp__<server 前 16 字符>__<tool>__<hash>` 与 legacy 名 `mcp__<完整 server id>__<tool>`。
     * 借 [McpToolApiName] 生成，保证与 harness 实际编码规则一致。
     */
    private val builtinBrowserServerSegments: Set<String> by lazy {
        val probe = McpToolInfo(
            serverId = BuiltinMcpPresets.BROWSER_BUILTIN_ID,
            serverName = BuiltinMcpPresets.BROWSER_BUILTIN_ID,
            name = "browser_snapshot",
            description = "",
        )
        setOf(McpToolApiName.encode(probe), McpToolApiName.legacy(probe))
            .map { it.substringAfter("__").substringBefore("__") }
            .toSet()
    }

    fun decide(
        mode: ApprovalMode,
        tool: HarnessTool,
        args: JsonObject,
        workspace: String,
        rawToolName: String? = null,
    ): ApprovalDecision {
        // 宿主特权命令作用于真实 Android 系统。只读操作始终放行；
        // 完全访问 = 用户显式授权一切宿主操作（含 exec / 卸载应用），全部自动放行；
        // ASSISTED 下 GUI 感知/触控（screen_* / app_launch）自动放行——否则工作流智能体节点
        // 会卡在 Harness 审批且工作流 UI 不展示该审批；危险变更（settings/package/exec）仍需确认。
        // REQUEST 模式对所有可变宿主操作仍要求确认。
        if (tool == HarnessTool.HOST) {
            val action = args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase()
            if (action in HOST_READ_ONLY_ACTIONS) {
                return ApprovalDecision(false)
            }
            if (mode == ApprovalMode.FULL_ACCESS) {
                return ApprovalDecision(false)
            }
            if (mode == ApprovalMode.ASSISTED && action in HOST_GUI_ASSISTED_ACTIONS) {
                return ApprovalDecision(false)
            }
            val critical = action == "exec" || action == "package_uninstall_user"
            return ApprovalDecision(
                required = true,
                riskLevel = if (critical) "critical" else "high",
                reason = when (action) {
                    "settings_put" -> "操作将修改真实 Android 系统设置。"
                    "package_disable", "package_enable", "app_freeze", "app_unfreeze" -> "操作将改变真实 Android 应用的启用状态。"
                    "app_grant_permission" -> "操作将为真实 Android 应用授予权限。"
                    "package_uninstall_user" -> "操作将为指定 Android 用户卸载应用；系统应用通常可用 install-existing 恢复，但其数据可能丢失。"
                    "screen_capture" -> "操作会把真实 Android 屏幕内容写入指定文件。"
                    "screen_click", "screen_swipe", "screen_input_text", "screen_key", "app_launch" ->
                        "操作将操控真实 Android 屏幕或启动应用。"
                    else -> "命令将通过 Shizuku 或 Root 修改真实 Android 宿主，可能改变系统设置、停用或卸载应用。"
                },
                summary = summarize(tool, args),
            )
        }
        if (mode == ApprovalMode.FULL_ACCESS) return ApprovalDecision(false)
        // use_capability 的 list/inspect/decline 是只读元操作（不启动任何服务器进程），任何模式免审
        if (tool == HarnessTool.MCP && rawToolName == "use_capability" &&
            args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() != "call"
        ) {
            return ApprovalDecision(false)
        }
        // 内置浏览器 MCP 工具按风险矩阵细化审批：只读（LOW）工具在任何模式下免审。
        // use_capability(call) 从 arguments 里取 (server, tool) 合成内部工具名，套用同一矩阵。
        if (tool == HarnessTool.MCP && mcpBrowserRisk(effectiveMcpToolName(tool, args, rawToolName)) == "low") {
            return ApprovalDecision(false)
        }
        if (tool == HarnessTool.READ || tool == HarnessTool.MEMORY || tool == HarnessTool.PLAN ||
            tool == HarnessTool.SCRATCHPAD || tool == HarnessTool.HISTORY_SEARCH || tool == HarnessTool.HISTORY_READ || tool == HarnessTool.LOAD_RULE || tool == HarnessTool.LOAD_SKILL || tool == HarnessTool.COMPRESS || tool == HarnessTool.ASK_USER || tool == HarnessTool.RENDER_SURFACE
        ) {
            return ApprovalDecision(false)
        }
        if (tool == HarnessTool.SUBAGENT) return ApprovalDecision(false)
        if (tool == HarnessTool.BUILD_SCRIPT && args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() in setOf("list", "get")) return ApprovalDecision(false)
        if (tool == HarnessTool.PROCESS && processAction(args) in setOf("status", "logs", "list")) {
            return ApprovalDecision(false)
        }

        val summary = summarize(tool, args, rawToolName)
        if (mode == ApprovalMode.REQUEST) {
            return ApprovalDecision(true, "normal", "当前权限模式要求所有会改变状态或产生外部副作用的工具操作先获得批准。", summary)
        }

        return when (tool) {
            HarnessTool.WRITE, HarnessTool.EDIT -> {
                val path = args["path"]?.jsonPrimitive?.content.orEmpty()
                if (workspace.isNotBlank() && isWithinWorkspace(path, workspace)) {
                    ApprovalDecision(false)
                } else {
                    ApprovalDecision(true, "high", "写入目标不在当前工作区内，可能影响工作区之外的文件。", summary)
                }
            }
            HarnessTool.BASE -> {
                val command = args["command"]?.jsonPrimitive?.content.orEmpty()
                if (isRoutineCommand(command)) ApprovalDecision(false)
                else ApprovalDecision(true, if (isDestructiveCommand(command)) "critical" else "high", reasonForCommand(command), summary)
            }
            HarnessTool.PROCESS -> when (processAction(args)) {
                "start" -> {
                    val command = args["command"]?.jsonPrimitive?.content.orEmpty()
                    if (isRoutineCommand(command)) ApprovalDecision(false)
                    else ApprovalDecision(true, if (isDestructiveCommand(command)) "critical" else "high", reasonForCommand(command), summary)
                }
                "stop" -> ApprovalDecision(true, "high", "停止操作会终止一个由 TianXuan 托管的后台进程。", summary)
                else -> ApprovalDecision(false)
            }
            HarnessTool.DOWNLOAD -> ApprovalDecision(true, "high", "下载会访问外部网络并写入工作区文件。", summary)
            HarnessTool.BUILD_SCRIPT -> ApprovalDecision(true, "normal", "操作将修改构建脚本或项目挂载关系。", summary)
            HarnessTool.HOST -> error("HOST 已在审批策略入口处理")
            HarnessTool.MCP -> when (mcpBrowserRisk(effectiveMcpToolName(tool, args, rawToolName))) {
                "medium" -> ApprovalDecision(true, "medium", "浏览器操作会改变页面状态或新开会话。", summary)
                "high" -> ApprovalDecision(true, "high", "浏览器操作将修改页面内容或写入本地存储。", summary)
                "critical" -> ApprovalDecision(true, "critical", "浏览器操作涉及代码执行或读取敏感数据（Cookie/页面源码）。", summary)
                else -> ApprovalDecision(true, "high", "MCP 工具可能访问外部服务或产生工作区之外的副作用。", summary)
            }
            // 刻意不写 else：本 when 必须穷尽 HarnessTool 全部成员。新增成员若漏配策略，
            // 编译器会报 "must be exhaustive"；写了 else 则被静默判为免审，成为免审后门。
            HarnessTool.READ, HarnessTool.MEMORY, HarnessTool.PLAN, HarnessTool.SCRATCHPAD,
            HarnessTool.HISTORY_SEARCH, HarnessTool.HISTORY_READ, HarnessTool.SUBAGENT, HarnessTool.LOAD_RULE,
            HarnessTool.LOAD_SKILL, HarnessTool.RENDER_SURFACE, HarnessTool.COMPRESS, HarnessTool.ASK_USER ->
                ApprovalDecision(false)
        }
    }

    /**
     * PLAN（只读规划）门禁：返回非 null 表示该调用在只读模式下被宿主硬拒绝，
     * 直接以失败结果回执给模型，**不进入审批队列**（用户本就没打算执行，弹审批卡只是噪音）。
     * 返回 null 表示放行，继续交给 [decide] 按审批模式判定。
     *
     * 与 [ApprovalMode] 正交：即便 `FULL_ACCESS + PLAN` 也只读——「我全权授权，但这次只让你看」。
     * 这里刻意不复用 [isRoutineCommand]：它把 `./gradlew test`、`npm run build`
     * 视为例常放行（会写构建产物、可能联网），在只读规划下必须当作写操作拦下。
     */
    fun planBlock(
        tool: HarnessTool,
        args: JsonObject,
        rawToolName: String? = null,
    ): String? = when (tool) {
        // 只读检索与元操作：全放
        HarnessTool.READ, HarnessTool.MEMORY, HarnessTool.PLAN, HarnessTool.SCRATCHPAD,
        HarnessTool.HISTORY_SEARCH, HarnessTool.HISTORY_READ, HarnessTool.LOAD_RULE,
        HarnessTool.LOAD_SKILL, HarnessTool.COMPRESS, HarnessTool.ASK_USER -> null
        HarnessTool.BASE -> if (isReadOnlyCommand(args["command"]?.jsonPrimitive?.content.orEmpty())) {
            null
        } else {
            "base 命令不是只读检索：只读规划模式仅允许 ls / cat / rg / grep / find / git status 等查看类命令，不允许安装、构建、测试或写文件。"
        }
        HarnessTool.PROCESS -> if (processAction(args) in setOf("status", "logs", "list")) {
            null
        } else {
            "process 仅允许查看（status / logs / list），不允许启动或停止进程。"
        }
        HarnessTool.HOST ->
            if (args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() in HOST_READ_ONLY_ACTIONS) {
                null
            } else {
                "宿主操作仅允许只读查询（status / settings_get / package_list / app_list / logcat / device_status / screen_observe），不允许改动真实 Android 系统。"
            }
        HarnessTool.MCP -> if (isReadOnlyMcpCall(args, rawToolName)) {
            null
        } else {
            "MCP 仅允许只读能力查询（use_capability 的 list / inspect / decline）与低风险浏览器只读工具。"
        }
        HarnessTool.BUILD_SCRIPT -> if (args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() in setOf("list", "get")) {
            null
        } else {
            "build_script 仅允许查看（list / get），不允许创建、绑定或删除脚本。"
        }
        // 其余（write / edit / download / subagent 等）一律拦截。
        else -> "该工具会写入文件、访问外部网络或改变状态，只读规划模式下不可用。"
    }

    /** MCP 只读判定：use_capability 的只读元操作，或内置浏览器风险矩阵中的 low 档工具。 */
    private fun isReadOnlyMcpCall(args: JsonObject, rawToolName: String?): Boolean {
        if (rawToolName == "use_capability" &&
            args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() != "call"
        ) {
            return true
        }
        return mcpBrowserRisk(effectiveMcpToolName(HarnessTool.MCP, args, rawToolName)) == "low"
    }

    fun createRequest(
        sessionId: String,
        toolCall: ToolCall,
        workspace: String,
        decision: ApprovalDecision,
        operationId: String? = null,
    ): AgentApprovalRequestEntity {
        val now = System.currentTimeMillis()
        return AgentApprovalRequestEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            toolCallId = toolCall.id,
            toolName = toolCall.rawToolName ?: toolCall.tool.name.lowercase(),
            argumentsJson = toolCall.args.toString(),
            workspace = workspace,
            riskLevel = decision.riskLevel,
            reason = decision.reason,
            summary = decision.summary,
            createdAt = now,
            operationId = operationId,
            argsHash = argsHash(toolCall.args.toString()),
            expiresAt = now + APPROVAL_TTL_MS,
        )
    }

    companion object {
        /** 审批有效期：超时未决的请求自动失效，恢复执行前也会复核。 */
        const val APPROVAL_TTL_MS: Long = 10 * 60 * 1000L
        private val HOST_READ_ONLY_ACTIONS = setOf(
            "status",
            "settings_get",
            "package_list",
            "app_list",
            "logcat",
            "device_status",
            "screen_observe",
        )
        /** ASSISTED 下自动放行的 GUI 原语（仍受 REQUEST / 危险动作策略约束）。 */
        private val HOST_GUI_ASSISTED_ACTIONS = setOf(
            "screen_click",
            "screen_double_click",
            "screen_long_press",
            "screen_swipe",
            "screen_scroll",
            "screen_input_text",
            "paste_text",
            "screen_key",
            "app_launch",
        )

        /** argumentsJson 的 SHA-256 十六进制摘要；创建时写入，执行前复核。 */
        fun argsHash(argumentsJson: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(argumentsJson.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        private val CD_PREFIX = Regex("""^cd\s+[^\s;&|<>`$()]+$""")

        /**
         * 管道过滤段白名单。只保留参数无法执行外部命令、也无法写文件的过滤命令：
         * awk（system()/getline 是完整编程语言）、rg（--pre 可指定任意预处理器命令）、
         * sort（-o/--output 可写任意路径）曾在此列，均因存在执行/写入面被移出——
         * 出现在过滤段时一律需要审批。
         */
        private val SAFE_READ_FILTER = Regex(
            """^(head|tail|grep|wc|cat|uniq|tr|cut)(\s|$)""",
        )

        /** "看似只读实则可执行"的特性黑名单：awk system()/getline、rg --pre 等（纵深防御）。
         *  --pre 用 (\s|=|$) 而非 \b 结尾：`--pre-glob` 是无害旗标不应误伤，`--pre=`/`--pre x` 必须命中。 */
        private val UNSAFE_EXEC_FEATURES = Regex("""system\s*\(|getline|popen|\|&|--pre(\s|=|$)""")

        /** find 的落盘/交互执行原语：-fls/-fprint 系列写任意文件，-ok 系列交互执行。 */
        private val FIND_WRITE_OR_EXEC = Regex(
            "\\bfind\\b.*\\s(-delete|-exec|-execdir|-ok|-okdir|-fprint|-fprint0|-fprintf|-fls)\\b",
        )
        private val BLOCKED_NETWORK_OR_MUTATION = Regex(
            """\b(curl|wget|nc|ssh|scp|adb|tianxuan-host|mount|umount|kill|pkill|chmod|chown|rm|mv|cp|tee|apt(-get)?|apk|dnf|pacman|npm\s+(install|publish)|pip\s+install|git\s+(push|reset|clean))\b""",
        )
        private val ROUTINE_PRIMARY = Regex(
            """^(pwd|ls|find|rg|grep|head|tail|cat|git\s+(status|diff|log|show)|gradle(w)?\b.*(test|check|assemble)|npm\s+(test|run\s+(test|lint|build))|flutter\s+(test|analyze|build)|pytest\b|kotlinc\b|\./gradlew\b.*(test|check|assemble))""",
        )

        /** PLAN 只读主命令白名单：纯查看，不含任何构建/测试/包管理入口。 */
        private val READ_ONLY_PRIMARY = Regex(
            """^(pwd|ls|find|rg|grep|head|tail|cat|wc|uniq|tr|cut|file|stat|du|df|git\s+(status|diff|log|show))\b""",
        )
    }

    private fun summarize(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String = when (tool) {
        HarnessTool.WRITE, HarnessTool.EDIT -> "${tool.name.lowercase()} ${args["path"]?.jsonPrimitive?.content.orEmpty()}"
        HarnessTool.BASE -> args["command"]?.jsonPrimitive?.content.orEmpty().lineSequence().firstOrNull().orEmpty()
        HarnessTool.PROCESS -> "process ${processAction(args)} ${args["id"]?.jsonPrimitive?.content.orEmpty()}".trim()
        HarnessTool.DOWNLOAD -> "download ${args["destination"]?.jsonPrimitive?.content.orEmpty()}"
        HarnessTool.HOST -> "host ${args["action"]?.jsonPrimitive?.content.orEmpty()} ${args["command"]?.jsonPrimitive?.content.orEmpty().lineSequence().firstOrNull().orEmpty()}".trim()
        HarnessTool.MCP -> {
            if (rawToolName == "use_capability") {
                val server = args["server"]?.jsonPrimitive?.content.orEmpty()
                val inner = args["tool"]?.jsonPrimitive?.content.orEmpty()
                if (inner.isNotBlank()) "MCP $server.$inner"
                else "MCP ${args["action"]?.jsonPrimitive?.content ?: "能力查询"}"
            } else {
                val toolName = rawToolName?.substringAfter("__")?.substringAfter("__")?.substringBefore("__")
                    ?: args["name"]?.jsonPrimitive?.content
                "MCP ${toolName ?: "工具调用"}"
            }
        }
        HarnessTool.BUILD_SCRIPT -> "build_script ${args["action"]?.jsonPrimitive?.content.orEmpty()} ${args["name"]?.jsonPrimitive?.content.orEmpty()}".trim()
        else -> tool.name.lowercase()
    }

    /**
     * 解析 MCP 工具名（mcp__<server>__<tool>__<hash>）并映射内置浏览器工具风险档位。
     * 仅当 server 段确认为内置 browser server（编码截断段或 legacy 完整 id）时才套用浏览器风险矩阵，
     * 防止外部 MCP server 用同名工具冒充内置白名单；非内置浏览器工具返回 null。
     */
    /**
     * 解析代理调用的实际目标工具名：use_capability(call) 从 arguments 合成
     * mcp__<server>__<tool> 形式以复用浏览器风险矩阵；其余原样返回 rawToolName。
     */
    private fun effectiveMcpToolName(tool: HarnessTool, args: JsonObject, rawToolName: String?): String? {
        if (tool != HarnessTool.MCP) return null
        if (rawToolName == "use_capability") {
            if (args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase() != "call") return null
            val server = args["server"]?.jsonPrimitive?.content.orEmpty().trim()
            val inner = args["tool"]?.jsonPrimitive?.content.orEmpty().trim()
            if (server.isBlank() || inner.isBlank()) return null
            return "mcp__${server}__${inner}"
        }
        return rawToolName
    }

    private fun mcpBrowserRisk(rawToolName: String?): String? {
        if (rawToolName == null) return null
        val server = rawToolName.substringAfter("__").substringBefore("__")
        if (server !in builtinBrowserServerSegments) return null
        val tool = rawToolName.substringAfter("__").substringAfter("__").substringBefore("__")
        return when (tool) {
            "browser_back", "browser_forward", "browser_refresh", "browser_list_tabs", "browser_close_tab",
            "browser_snapshot", "browser_scroll", "browser_screenshot", "browser_current_url", "browser_title",
            "browser_console_list", "browser_network_list" -> "low"
            "browser_open", "browser_navigate", "browser_page_source", "browser_console_clear",
            "browser_local_keys", "browser_session_keys" -> "medium"
            "browser_click", "browser_type", "browser_press", "browser_cookies_set", "browser_cookies_delete",
            "browser_local_get", "browser_local_set", "browser_local_delete",
            "browser_session_get", "browser_session_set", "browser_session_delete" -> "high"
            "browser_evaluate", "browser_cookies_get" -> "critical"
            else -> null
        }
    }

    private fun processAction(args: JsonObject): String =
        args["action"]?.jsonPrimitive?.content.orEmpty().trim().lowercase()

    private fun isWithinWorkspace(path: String, workspace: String): Boolean =
        pathResolver.isWithinWorkspace(path, workspace)

    private fun isRoutineCommand(command: String): Boolean =
        isSafeReadPipeline(command.trim().lowercase()) { isRoutinePrimaryCommand(it) }

    /**
     * PLAN 只读门禁专用的**更窄**白名单：只承认「查看」类主命令。
     *
     * 不复用 [isRoutineCommand]：后者把 `./gradlew test|assemble`、`npm run build`、
     * `flutter build` 也当例常放行（会写构建产物、可能联网），在只读规划下都属于写操作。
     */
    private fun isReadOnlyCommand(command: String): Boolean =
        isSafeReadPipeline(command.trim().lowercase()) { isReadOnlyPrimaryCommand(it) }

    /**
     * 管道/组合命令的统一安全解析（[primary] 决定主命令是否放行）：
     * 仅一条主命令（允许一个 `cd <path> &&` 前缀），管道后续段只能是只读过滤器，
     * 且先排除动态 shell 语法与文件重定向。
     */
    private fun isSafeReadPipeline(normalized: String, primary: (String) -> Boolean): Boolean {
        if (normalized.isBlank()) return false
        if (isDestructiveCommand(normalized)) return false
        // Block dynamic shell syntax before whitelist matching. Command substitution, backtick
        // expansion, eval, and nested shells can hide a second mutation behind an otherwise
        // harmless prefix (e.g. `ls $(rm -rf /tmp)`). Require explicit approval for these.
        if (hasDynamicShellSyntax(normalized)) return false
        if (listOf("\n", "\r", ";", "||", "`", "$(").any { it in normalized }) return false
        if (hasUnsafeFileRedirection(normalized)) return false

        // Allow token-saving read pipelines and `cd <path> && <routine>` composition that the
        // agent prompt itself recommends (e.g. `ls ... 2>&1 | head -40`, `cd proj && rg ... | head`).
        val withoutSafeRedirects = normalized
            .replace(Regex("""\s+\d*>&\d+\b"""), " ")
            .replace(Regex("""\s+>&\d+\b"""), " ")
            .trim()
        // 裸 `&`（后台执行符）不可自动放行：`cat pom.xml & rm x` 的后半段会在 shell
        // 后台直接执行并完全绕过前缀白名单。必须先剥 `2>&1` 类 fd 重定向、拆掉 `&&`
        // 连接符（两者安全且有专门处理），剩余任何 `&` 都是未覆盖的组合语法，一律审批。
        if ("&" in withoutSafeRedirects.replace("&&", " ")) return false
        val pipeParts = withoutSafeRedirects.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (pipeParts.isEmpty()) return false
        if (pipeParts.drop(1).any { !isSafeReadFilter(it) }) return false

        val leftParts = pipeParts.first().split("&&").map { it.trim() }.filter { it.isNotEmpty() }
        if (leftParts.isEmpty()) return false
        var index = 0
        if (CD_PREFIX.matches(leftParts[0])) {
            index = 1
            if (index >= leftParts.size) return false
        }
        // Only one primary command after optional cd — further && chains need approval.
        if (leftParts.size - index != 1) return false
        return primary(leftParts[index])
    }

    private fun isRoutinePrimaryCommand(command: String): Boolean {
        // 黑名单匹配必须在去混淆文本上进行（见 [shellDeobfuscated]）；白名单正匹配仍用
        // 原文——混淆形态的直接不自动放行，归一化只负责放大拦截面。
        val deobfuscated = shellDeobfuscated(command)
        if (FIND_WRITE_OR_EXEC.containsMatchIn(deobfuscated)) return false
        // rg --pre 可执行任意预处理器命令，rg 同时是白名单首选检索命令，必须单独拦。
        if (UNSAFE_EXEC_FEATURES.containsMatchIn(deobfuscated)) return false
        if (BLOCKED_NETWORK_OR_MUTATION.containsMatchIn(deobfuscated)) return false
        return ROUTINE_PRIMARY.containsMatchIn(command)
    }

    /** PLAN 只读主命令：与 [isRoutinePrimaryCommand] 同源防护，但白名单更窄（纯查看）。 */
    private fun isReadOnlyPrimaryCommand(command: String): Boolean {
        val deobfuscated = shellDeobfuscated(command)
        if (FIND_WRITE_OR_EXEC.containsMatchIn(deobfuscated)) return false
        if (UNSAFE_EXEC_FEATURES.containsMatchIn(deobfuscated)) return false
        if (BLOCKED_NETWORK_OR_MUTATION.containsMatchIn(deobfuscated)) return false
        return READ_ONLY_PRIMARY.containsMatchIn(command)
    }

    private fun isSafeReadFilter(command: String): Boolean {
        if (!SAFE_READ_FILTER.containsMatchIn(command)) return false
        if (hasDynamicShellSyntax(command)) return false
        return !UNSAFE_EXEC_FEATURES.containsMatchIn(shellDeobfuscated(command))
    }

    /**
     * shell 词法近似：剥引号并折叠反斜杠转义（\c → c）。`find "-exec" rm`、`rg --p\re`
     * 在原文上匹配不到旗标，shell 却会还原成实际 argv。归一化只用于黑名单匹配（宁可
     * 多审批）；已知误伤：`grep -n "system()" f`（检索字面量）会被要求审批——安全方向。
     */
    private fun shellDeobfuscated(command: String): String =
        command.replace("\"", "").replace("'", "").replace("\\", "")

    /** File redirection (`>` / `<`) except fd-to-fd forms like `2>&1`. */
    private fun hasUnsafeFileRedirection(command: String): Boolean {
        val stripped = command
            .replace(Regex("""\d*>&\d+"""), "")
            .replace(Regex(""">&\d+"""), "")
        return ">" in stripped || "<" in stripped
    }

    /**
     * Detect dynamic shell syntax patterns that could bypass whitelist-based auto-approval.
     *
     * Even if a command's prefix matches a known-safe pattern (e.g. starts with `ls`),
     * command substitution or nested-shell invocations can carry arbitrary side effects.
     * These constructs require explicit user approval regardless of the command prefix.
     *
     * Detected patterns:
     * - `$(...)` — command substitution
     * - `` `...` `` — backtick command substitution (already covered by the `\`` check in
     *   the caller, but also matched here for clarity and defence-in-depth)
     * - `eval` / `source` — delayed execution of arbitrary code
     * - `sh -c` / `bash -c` / `ksh -c` — nested shell with inline command string
     */
    private fun hasDynamicShellSyntax(command: String): Boolean {
        // Command substitution: $( or backtick (backtick also caught by caller's contains check)
        if (command.contains("\$(") || command.contains("`")) return true
        // Nested-shell invocation patterns
        if (Regex("""\b(eval|source)\b""").containsMatchIn(command)) return true
        if (Regex("""\b(sh|bash|ksh|zsh|dash)\s+-c\b""").containsMatchIn(command)) return true
        return false
    }

    private fun isDestructiveCommand(command: String): Boolean = listOf(
        Regex("\\brm\\s+-(?:[^\\s]*r[^\\s]*f|[^\\s]*f[^\\s]*r)\\b", RegexOption.IGNORE_CASE),
        Regex("\\brm\\s+-r\\b", RegexOption.IGNORE_CASE),
        Regex("\\brm\\s+-rf?\\s+--no-preserve-root"),
        Regex("\\bmkfs\\.", RegexOption.IGNORE_CASE),
        Regex("\\bdd\\s+if=.*\\bof=/dev/", RegexOption.IGNORE_CASE),
        Regex("\\b(shutdown|reboot|halt|poweroff)\\b"),
        Regex("\\btruncate\\s+-s\\s+0\\b", RegexOption.IGNORE_CASE),
        Regex(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\}"),
        Regex(">\\s*/dev/(sd|mmcblk|nvme)", RegexOption.IGNORE_CASE),
        Regex("\\bchmod\\s+-r\\s+777\\s+/", RegexOption.IGNORE_CASE),
    ).any { it.containsMatchIn(command) }

    private fun reasonForCommand(command: String): String = if (isDestructiveCommand(command)) {
        "命令匹配到删除、格式化、设备写入或关机等不可逆高危模式。"
    } else {
        "命令可能安装软件、访问外部网络、修改系统状态或影响工作区之外的资源。"
    }
}
