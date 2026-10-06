package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.harness.mcp.McpToolApiName
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.tianxuan.harness.validation.ToolSchemaValidator
import top.wkbin.tianxuan.harness.validation.ToolCallLoopDetector
import top.wkbin.tianxuan.harness.metrics.RunMetrics
import top.wkbin.tianxuan.harness.effects.ToolReplayPolicy
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import top.wkbin.tianxuan.harness.projection.SessionMessageProjector
import top.wkbin.tianxuan.harness.projection.SessionStateMirrors
import top.wkbin.tianxuan.harness.projection.ToolStatusDescriber

/** 已通过串行校验、待并发执行的工具调用。 */
private data class ExecutableToolCall(
    val spec: ApiToolCallSpec,
    val tool: HarnessTool,
    val toolName: String,
    val args: JsonObject,
)

/** 工具回合边界：参数校验、限额、执行、审批暂停和结果结算。 */
class HarnessToolRoundRunner(
    private val toolExecutor: ToolExecutor,
    private val sessionDao: HarnessSessionRepository,
    private val json: Json,
    private val operationCoordinator: OperationCoordinator,
    private val messageProjector: SessionMessageProjector,
    private val stateMirrors: SessionStateMirrors,
    private val agentEventLogger: AgentEventLogger,
    private val toolRoundDispatcher: ToolRoundDispatcher,
) {
    /** 单轮工具数上限：超出部分回填空结果并提示模型，返回保留执行的前 maxToolsPerRound 个调用 */
    suspend fun enforceToolRoundLimit(
        sessId: String,
        allCalls: List<ApiToolCallSpec>,
        maxToolsPerRound: Int,
        reasoning: String?,
    ): List<ApiToolCallSpec> {
        if (allCalls.size <= maxToolsPerRound) return allCalls
        val dropped = allCalls.size - maxToolsPerRound
        allCalls.drop(maxToolsPerRound).forEach { spec ->
            appendToolCallAndResult(
                sessId = sessId,
                spec = spec,
                tool = HarnessApiMapper.toolByName(spec.name),
                args = buildJsonObject {},
                reasoning = reasoning,
                rawToolName = spec.name.trim(),
                output = "本回合工具调用数量（${allCalls.size}）超过单轮上限（$maxToolsPerRound），已跳过本次多余的 $dropped 个调用。" +
                    "请拆分任务、分步调用工具，避免一次性发起过多工具请求。",
            )
        }
        return allCalls.take(maxToolsPerRound)
    }

    /** 失败工具调用的统一样板：先回放 ToolCall 气泡，再写回失败 ToolResult 让模型自我纠正 */
    private suspend fun appendToolCallAndResult(
        sessId: String,
        spec: ApiToolCallSpec,
        tool: HarnessTool,
        args: JsonObject,
        reasoning: String?,
        rawToolName: String?,
        output: String,
    ) {
        val toolCallId = ToolCallIdNormalizer.normalize(spec.id)
        // 拦截类结果只进消息流不进事件日志的话，日志里会出现「响应带 toolCall 却无 ToolCall 记录」的假象
        agentEventLogger.log(
            sessId,
            "ToolCallRejected",
            "Tool=${tool.name}, CallId=$toolCallId, RawTool=${rawToolName ?: "unknown"}: $output",
        )
        messageProjector.append(
            sessId,
            ToolCall(
                id = toolCallId,
                createdAt = now(),
                tool = tool,
                args = args,
                reasoning = reasoning,
                rawToolName = rawToolName,
            ),
        )
        messageProjector.append(
            sessId,
            ToolResult(
                id = newId(),
                createdAt = now(),
                toolCallId = toolCallId,
                success = false,
                output = output,
            ),
        )
    }

    /** 参数解析 → 未知工具 → Schema 校验 → 死循环检测（串行 Phase A）→ 受限并发执行与结果落盘（Phase B）；返回本回合是否有成功调用 */
    suspend fun executeToolCalls(
        sessId: String,
        specs: List<ApiToolCallSpec>,
        reasoning: String?,
        sessionWorkspace: String,
        autoCwd: Boolean,
        effectiveModel: ModelConfig,
        operationId: String,
        round: Int,
        metrics: RunMetrics,
        loopDetector: ToolCallLoopDetector,
    ): Boolean {

        // —— Phase A：串行校验。失败立即回写结构化错误让模型自纠；
        // 通过校验的调用收集后进入 Phase B 并发执行。
        val executable = mutableListOf<ExecutableToolCall>()
        specs.forEach { spec ->
            val tool = HarnessApiMapper.toolByName(spec.name)
            val toolNameTrimmed = spec.name.trim()
            // 工具名校验必须在参数解析之前：名字未知时（哪怕参数为空/非法）
            // 也要第一时间回写真实工具清单，否则模型会在"解析失败"上盲目重试。
            if (toolNameTrimmed.lowercase() !in KNOWN_TOOL_NAMES && !toolNameTrimmed.startsWith("mcp__")) {
                appendToolCallAndResult(
                    sessId = sessId,
                    spec = spec,
                    tool = tool,
                    args = buildJsonObject {},
                    reasoning = reasoning,
                    rawToolName = toolNameTrimmed,
                    output = unknownToolGuidance(toolNameTrimmed, effectiveModel),
                )
                loopDetector.recordSettled(toolNameTrimmed, buildJsonObject {}, success = false)
                metrics.toolCallRecorded(failed = true)
                return@forEach
            }
            val parsedArgs = try {
                parseArguments(json, spec.argumentsJson)
            } catch (parseError: IllegalArgumentException) {
                appendToolCallAndResult(
                    sessId = sessId,
                    spec = spec,
                    tool = tool,
                    args = buildJsonObject {},
                    reasoning = reasoning,
                    rawToolName = toolNameTrimmed,
                    output = "工具参数 JSON 解析失败（${friendly(parseError)}）。请重新发起完整的工具调用，参数必须是合法的 JSON 对象。",
                )
                loopDetector.recordSettled(toolNameTrimmed, buildJsonObject {}, success = false)
                metrics.toolCallRecorded(failed = true)
                return@forEach
            }
            var args = parsedArgs
            if (tool == HarnessTool.BASE && autoCwd && sessionWorkspace.isNotBlank() && args["cwd"] == null) {
                args = buildJsonObject {
                    put("cwd", sessionWorkspace)
                    args.forEach { (key, value) -> put(key, value) }
                }
            }
            // 执行前 JSON Schema 校验：必填/枚举/范围/格式/组合约束。
            // 失败时写回可读问题清单，让模型按 schema 自我纠正，而不是带着坏参数进入执行层。
            val schemaProblems = ToolSchemaValidator.problemsFor(toolNameTrimmed, args, effectiveModel.dynamicMcpTools)
            if (schemaProblems.isNotEmpty()) {
                // 常见错配定向提示：模型想把 url 交给通用 shell 时，直接指向正确的专用工具
                val urlHint = if (args.containsKey("url") && tool != HarnessTool.DOWNLOAD) {
                    "提示：url 是 download 工具的参数，下载网页/图片/文件请调用 download(url, destination)。"
                } else ""
                appendToolCallAndResult(
                    sessId = sessId,
                    spec = spec,
                    tool = tool,
                    args = args,
                    reasoning = reasoning,
                    rawToolName = toolNameTrimmed,
                    output = "工具参数校验未通过：${schemaProblems.joinToString("；")}。" +
                        "请按工具定义修正参数后重新调用，必填字段不可省略。$urlHint",
                )
                loopDetector.recordSettled(toolNameTrimmed, args, success = false)
                metrics.toolCallRecorded(failed = true)
                return@forEach
            }

            // 执行前死循环与重复无进展调用检测：阻断重复错误重试与空转
            val loopVerdict = loopDetector.evaluate(toolNameTrimmed, args)
            if (loopVerdict is ToolCallLoopDetector.LoopVerdict.Block) {
                appendToolCallAndResult(
                    sessId = sessId,
                    spec = spec,
                    tool = tool,
                    args = args,
                    reasoning = reasoning,
                    rawToolName = toolNameTrimmed,
                    output = loopVerdict.guidance,
                )
                loopDetector.recordSettled(toolNameTrimmed, args, success = false)
                metrics.toolCallRecorded(failed = true)
                return@forEach
            }

            loopDetector.recordIntent(toolNameTrimmed, args)
            executable += ExecutableToolCall(spec, tool, toolNameTrimmed, args)
        }

        if (executable.isEmpty()) return false

        // —— Phase B：受限并发执行。消息树落库（toolIntent / publishPersisted / toolSettled）
        // 依赖 lane.leafId 串链，必须串行，由 publicationMutex 保证；
        // 只读工具在并发许可内同时执行，变更类工具按工作区互斥（跨工作区不互相阻塞）。
        val publicationMutex = Mutex()
        val roundHadSuccess = AtomicBoolean(false)
        val approvalPauseRequested = AtomicBoolean(false)
        toolRoundDispatcher.dispatch(
            items = executable,
            mutationScope = sessionWorkspace,
            isParallelSafe = { it.tool in PARALLEL_SAFE_TOOLS || it.tool in SELF_COORDINATED_TOOLS },
        ) { item, pause ->
            if (pause.isAborted()) return@dispatch
            val toolCall = ToolCall(
                // Preserve the provider protocol id prefix with a unique suffix across
                // execution, approval, persistence and the subsequent tool result.
                id = ToolCallIdNormalizer.normalize(item.spec.id),
                createdAt = now(),
                tool = item.tool,
                args = item.args,
                reasoning = reasoning,
                rawToolName = item.toolName,
            )
            val toolStart = now()
            val outcome = try {
                publicationMutex.withLock {
                    agentEventLogger.log(sessId, "ToolCall", "Tool=${item.tool.name}, CallId=${toolCall.id}, ArgumentCount=${item.args.size}")
                    operationCoordinator.toolIntent(
                        operationId = operationId,
                        message = toolCall,
                        payloadJson = item.args.toString(),
                        replay = ToolReplayPolicy.forTool(item.tool, item.toolName),
                        round = round,
                    )
                    messageProjector.publishPersisted(sessId, toolCall)
                    stateMirrors.setStatus(sessId, ToolStatusDescriber.describe(item.tool, item.args, item.toolName))
                }
                toolExecutor.execute(
                    toolCall,
                    sessId,
                    sessionWorkspace,
                    progressReporter = { progress -> stateMirrors.setStatus(sessId, progress) },
                    operationId = operationId,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                ToolResult(
                    id = newId(),
                    createdAt = now(),
                    toolCallId = toolCall.id,
                    success = false,
                    output = "工具执行异常：${friendly(throwable)}",
                )
            }
            val duration = now() - toolStart
            publicationMutex.withLock {
                agentEventLogger.log(sessId, "ToolResult", "Tool=${item.tool.name}, CallId=${toolCall.id}, Success=${outcome.success}, Duration=${duration}ms, OutputChars=${outcome.output.length}, AwaitingApproval=${outcome.awaitingApproval}")
                loopDetector.recordSettled(item.toolName, item.args, success = outcome.success, output = outcome.output)
                if (outcome.awaitingApproval) {
                    metrics.approvalRequested()
                    operationCoordinator.waitingApproval(operationId)
                    stateMirrors.setStatus(sessId, "等待用户批准")
                    // 触发审批暂停：中止本回合尚未开始的调用，在途调用自然完成后统一暂停，
                    // 与原串行实现"中途暂停、后续调用不执行"的语义一致。
                    pause.abort()
                    approvalPauseRequested.set(true)
                }
                val settledOutcome = outcome.copy(durationMs = duration)
                operationCoordinator.toolSettled(operationId, settledOutcome, round, toolName = toolCall.rawToolName ?: item.tool.name)
                messageProjector.publishPersisted(sessId, settledOutcome)
                if (outcome.success) roundHadSuccess.set(true)
                metrics.toolCallRecorded(failed = !outcome.success)
                sessionDao.touch(sessId, now())
            }
        }
        if (approvalPauseRequested.get()) throw ApprovalPauseException()
        return roundHadSuccess.get()
    }

    /**
     * 未知工具的可纠正错误：不写死固定话术，而是列出当前真实可用的全部工具名
     * （原生工具 + 已启用 MCP 的实际 API 名），并按编辑距离提示最接近的候选。
     * 模型幻觉出工具名（如 fetchWebContent）时能一次拿到正确名字，不再反复编造。
     */
    private fun unknownToolGuidance(called: String, model: ModelConfig): String {
        val nativeTools = KNOWN_TOOL_NAMES.sorted()
        val mcpTools = model.dynamicMcpTools
        val mcpList = if (mcpTools.isEmpty()) {
            "（当前没有已启用的 MCP 工具）"
        } else {
            mcpTools.joinToString("；") { tool ->
                "${McpToolApiName.encode(tool)}（${tool.serverName}·${tool.name}）"
            }
        }
        val target = called.lowercase()
        val nearest = (nativeTools + mcpTools.map { McpToolApiName.encode(it) })
            .mapNotNull { candidate ->
                val distance = levenshtein(target, candidate.lowercase())
                if (distance <= (target.length / 2).coerceAtLeast(3)) candidate to distance else null
            }
            .minByOrNull { it.second }
            ?.first
        return buildString {
            append("未知工具：$called。工具名不可编造或猜测，必须从下列清单中原样选取。")
            append("原生工具：${nativeTools.joinToString(" / ")}。")
            append("已启用 MCP 工具：$mcpList。")
            nearest?.let { append("最接近的候选是 $it，是否想调用它？") }
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    prev[j] + 1,
                    current[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            prev = current
        }
        return prev[b.length]
    }

    private fun now(): Long = System.currentTimeMillis()
    private fun newId(): String = UUID.randomUUID().toString()
    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()
    companion object {
        // MCP 的 apiName "mcp" 只是历史回放别名，不是模型可直接调用的工具；
        // 剔除后模型误调 "mcp" 会落入 unknownToolGuidance，拿到真实 mcp__ 工具清单自我纠正。
        // "subagent"/"invoke_dual_agent" 是 invoke_subagent 的历史别名与双智能体变体
        // （ProviderClient 会向模型声明 invoke_dual_agent），必须一并放行，否则被自家拦截。
        val KNOWN_TOOL_NAMES: Set<String> = HarnessTool.entries
            .filter { it != HarnessTool.MCP }
            .map { HarnessApiMapper.apiName(it) }
            .toSet() + "subagent" + "invoke_dual_agent" + "use_capability"

        internal fun parseArguments(json: Json, raw: String): JsonObject =
            if (raw.isBlank()) buildJsonObject {} else {
                json.parseToJsonElement(raw) as? JsonObject
                    ?: throw IllegalArgumentException("参数不是 JSON 对象")
            }

        /**
         * 可并发执行的只读/低风险工具白名单：互不共享可变状态（Room 由 SQLite 串行化写入）。
         * 其余工具（write/edit/base/process/host/download/build_script/mcp）具有
         * 外部副作用，执行时全局互斥。
         */
        private val PARALLEL_SAFE_TOOLS: Set<HarnessTool> = setOf(
            HarnessTool.READ,
            HarnessTool.HISTORY_SEARCH,
            HarnessTool.HISTORY_READ,
            HarnessTool.LOAD_RULE,
            HarnessTool.MEMORY,
            HarnessTool.PLAN,
            HarnessTool.SCRATCHPAD,
        )

        /**
         * 自行协调写隔离、因此不参与变更互斥的编排型工具。
         *
         * invoke_subagent 是一次可达 15 分钟的整批编排：它内部按 write_paths 切波、
         * 逐个子任务串行拿写租约，工作区级互斥对它没有额外保护作用。让它整批持锁会把
         * 同工作区的其他 base/write/edit 一起挡住十几分钟（实测另一会话的 BASE 因此
         * 等了约 70 秒）。
         */
        private val SELF_COORDINATED_TOOLS: Set<HarnessTool> = setOf(HarnessTool.SUBAGENT)
    }
}

internal class ApprovalPauseException : RuntimeException()
