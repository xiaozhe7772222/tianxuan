package top.wkbin.tianxuan.harness.subagent

import android.content.Context
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.harness.ApiMessage
import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.CapabilityEvent
import top.wkbin.tianxuan.harness.ContextWindowPolicy
import top.wkbin.tianxuan.harness.HarnessApiMapper
import top.wkbin.tianxuan.harness.ModelSwitchEvent
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.HarnessTool
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.harness.SkillSuggestion
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolCallIdNormalizer
import top.wkbin.tianxuan.harness.ToolCallMode
import top.wkbin.tianxuan.harness.ToolExecutor
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.TextToolCallCodec
import top.wkbin.tianxuan.harness.effects.ToolReplayPolicy
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.prompt.PromptAssetLoader
import top.wkbin.tianxuan.harness.session.SessionTreeStore
import top.wkbin.tianxuan.harness.validation.ToolCallLoopDetector
import top.wkbin.tianxuan.harness.validation.ToolSchemaValidator
import top.wkbin.tianxuan.harness.R

data class SubagentLaneResult(
    val success: Boolean,
    val summary: String,
    val toolCallCount: Int,
    /** 终止原因。父层据此区分"真完成"与"收场但没做完"，不再只看 [success]。 */
    val termination: SubagentTermination = SubagentTermination.CONCLUDED,
    /** 需要审批因而未执行的调用，必须由主智能体在主会话重发。 */
    val pendingApprovals: List<SubagentApprovalHandoff> = emptyList(),
    /** 被写租约拦截的写入目标。 */
    val blockedWrites: List<String> = emptyList(),
)

/**
 * Headless lane interpreter used by subagents; it shares tree history but owns its operation.
 *
 * [ToolExecutor] 通过函数提供器延迟获取，打断依赖环：
 * ToolExecutor → SubagentOrchestrator → SubagentLaneRunner → ToolExecutor。
 * 工具只在 [run] 执行期才实际取用，构造期延迟解析是安全的。
 */
class SubagentLaneRunner(
    private val context: Context,
    private val providerClient: ProviderClient,
    private val toolExecutor: () -> ToolExecutor,
    private val treeStore: SessionTreeStore,
    private val operations: OperationCoordinator,
    private val settingsDataStore: AgentPreferences,
    private val promptAssets: PromptAssetLoader,
    private val json: Json,
) {
    suspend fun run(
        sessionId: String,
        laneName: String,
        prompt: String,
        workspace: String,
        modelId: String? = null,
        modelVariant: String? = null,
        modelConfig: top.wkbin.tianxuan.harness.ModelConfig? = null,
        /**
         * 子任务声明的写租约。非 null 时启用执行期写闸门：空列表 = 只读任务，
         * write/edit/download 在本 Lane 内被强制拦截。
         *
         * null（默认）= 不启用闸门，保留给不经 [SubagentOrchestrator] 编排的调用方
         * （双智能体执行者 Lane、workflow 推理节点）——它们没有写租约契约，
         * 闸门会把所有写入误拦成"未声明 write_paths 的只读任务"。
         */
        writePaths: List<String>? = null,
    ): SubagentLaneResult {
        val user = top.wkbin.tianxuan.harness.UserMessage(UUID.randomUUID().toString(), now(), prompt)
        val operationId = operations.acceptRun(sessionId, user, laneName)
        var toolCalls = 0
        var finalText = ""
        // 需要审批而被跳过的调用、被写租约拦截的写入：两者都会让"看起来收场了"实际没做完，
        // 因此必须跨轮累计，并参与最终的完成判定。
        val deferredApprovals = mutableListOf<SubagentApprovalHandoff>()
        val blockedWrites = mutableListOf<String>()
        // 写过但最后一次尝试仍失败的目标（同目标后续写成功会移除）。
        // 这是"声称已落盘、其实没写成"的结构化证据；靠在结论文本里匹配"写入失败"会误伤
        // 只读审计任务（"检查为什么 X 服务写入失败"的合法结论里也有这些词）。
        val failedWrites = linkedMapOf<String, String>()
        return try {
            val configuredModel = modelConfig ?: providerClient.resolveConfigured(modelId, modelVariant)
            val loopDetector = ToolCallLoopDetector()
            // 子智能体是定向小任务，不能沿用主会话数百轮上限，否则只读审计会漫游到超时。
            val toolRounds = runCatching { settingsDataStore.maxToolRounds.first() }
                .getOrDefault(DEFAULT_MAX_ROUNDS)
                .coerceIn(MIN_MAX_ROUNDS, MAX_MAX_ROUNDS)
            // 收束轮额外追加：最后一轮不向 Provider 暴露工具，若把它算进 toolRounds，
            // 用户配置 N 轮实际只有 N-1 轮能执行工具。
            val totalRounds = toolRounds + 1
            val historyBudgetTokens = laneHistoryBudget(configuredModel)
            repeat(totalRounds) { round ->
                val forceFinalAnswer = shouldForceSubagentFinalAnswer(round, totalRounds)
                val model = if (forceFinalAnswer) configuredModel.copy(pureChatMode = true) else configuredModel
                val responseId = UUID.randomUUID().toString()
                operations.providerIntent(operationId, responseId, round, 1, NETWORK_ATTEMPTS)
                val text = StringBuilder()
                val result = chatWithRetry(
                    model,
                    providerMessages(sessionId, laneName, forceFinalAnswer, configuredModel, historyBudgetTokens),
                    text,
                )
                val rawAssistantText = text.toString().ifBlank { result.content.orEmpty() }
                val textNormalization = TextToolCallCodec.normalize(json, rawAssistantText)
                val roundCalls = if (forceFinalAnswer) {
                    result.toolCalls
                } else {
                    TextToolCallCodec.resolveCalls(result.toolCalls, textNormalization)
                }
                val assistantText = if (textNormalization.hasMarkers) {
                    textNormalization.displayText
                } else {
                    rawAssistantText
                }
                val usageEntity = result.usage.takeIf { it.hasData }?.let {
                    operations.usageEntity(
                        sessionId = sessionId,
                        operationId = operationId,
                        entryId = responseId.takeIf { assistantText.isNotBlank() },
                        provider = model.provider,
                        modelId = model.model,
                        usage = it,
                    )
                }
                if (assistantText.isNotBlank()) {
                    val assistant = AssistantText(
                        id = responseId,
                        createdAt = now(),
                        text = assistantText,
                        reasoning = result.reasoningContent,
                        modelId = model.model,
                        providerId = model.provider,
                        promptTokens = result.usage.inputTokens.takeIf { it > 0 }?.toInt(),
                        completionTokens = result.usage.outputTokens.takeIf { it > 0 }?.toInt(),
                        cachedTokens = result.usage.cacheReadTokens.takeIf { it > 0 }?.toInt(),
                    )
                    operations.providerSettled(operationId, assistant, usage = usageEntity, round = round)
                    finalText = assistantText
                } else {
                    operations.providerSettled(operationId, null, usage = usageEntity, round = round)
                }
                if (roundCalls.isEmpty() && !forceFinalAnswer && textNormalization.hasUnresolvedMarkers) {
                    operations.finish(sessionId, "failed", details = "无法解析文本工具调用", laneName = laneName)
                    return SubagentLaneResult(
                        success = false,
                        summary = "模型返回了无法解析的文本工具调用，未将其误判为任务完成",
                        toolCallCount = toolCalls,
                        termination = SubagentTermination.UNPARSEABLE_TOOL_CALL,
                        pendingApprovals = deferredApprovals.toList(),
                        blockedWrites = blockedWrites.toList(),
                    )
                }
                // 收场判定：最后一轮，或普通轮次里模型不再调用工具。
                // 两条路径都必须走同一套判定——"没有工具调用"本身不是完成的证据：
                // 空输出、仍在输出工具协议、自述未完成、有待审批或被拦截的写入，都不算完成。
                if (forceFinalAnswer || roundCalls.isEmpty()) {
                    val verdict = judgeSubagentConclusion(
                        roundText = assistantText,
                        structuredCalls = result.toolCalls,
                        textNormalization = textNormalization,
                        deferredApprovals = deferredApprovals,
                        blockedWrites = blockedWrites,
                        unresolvedWriteFailures = failedWrites.keys.toList(),
                    )
                    operations.finish(
                        sessionId,
                        if (verdict.accepted) "completed" else "failed",
                        responseId.takeIf { assistantText.isNotBlank() },
                        details = verdict.reason.takeIf { it.isNotBlank() },
                        laneName = laneName,
                    )
                    return SubagentLaneResult(
                        success = verdict.accepted,
                        summary = laneSummary(verdict, assistantText, deferredApprovals, blockedWrites, failedWrites),
                        toolCallCount = toolCalls,
                        termination = verdict.termination,
                        pendingApprovals = deferredApprovals.toList(),
                        blockedWrites = blockedWrites.toList(),
                    )
                }

                for (spec in roundCalls) {
                    toolCalls++
                    val callId = ToolCallIdNormalizer.normalize(spec.id)
                    val rawName = spec.name.trim()
                    val tool = HarnessApiMapper.toolByName(rawName)
                    val args = runCatching { json.parseToJsonElement(spec.argumentsJson) as JsonObject }.getOrElse {
                        val invalidCall = ToolCall(callId, now(), tool, JsonObject(emptyMap()), result.reasoningContent, rawName)
                        operations.toolIntent(
                            operationId,
                            invalidCall,
                            spec.argumentsJson,
                            ToolReplayPolicy.forTool(tool, rawName),
                            round,
                        )
                        val failed = ToolResult(UUID.randomUUID().toString(), now(), callId, false, "工具参数不是 JSON 对象：${it.message}")
                        operations.toolSettled(operationId, failed, round, toolName = rawName)
                        continue
                    }
                    val call = ToolCall(callId, now(), tool, args, result.reasoningContent, rawName)
                    val schemaProblems = ToolSchemaValidator.problemsFor(rawName, args, model.dynamicMcpTools)
                    if (schemaProblems.isNotEmpty()) {
                        operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                        val rejected = ToolResult(
                            UUID.randomUUID().toString(), now(), callId, false,
                            "工具参数校验未通过：${schemaProblems.joinToString("；")}。请修正参数后重新调用。",
                        )
                        operations.toolSettled(operationId, rejected, round, toolName = rawName)
                        continue
                    }
                    val loopVerdict = loopDetector.evaluate(rawName, args)
                    if (loopVerdict is ToolCallLoopDetector.LoopVerdict.Block) {
                        operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                        val blocked = ToolResult(
                            UUID.randomUUID().toString(), now(), callId, false,
                            "${loopVerdict.reason}\n\n${loopVerdict.guidance}",
                        )
                        operations.toolSettled(operationId, blocked, round, toolName = rawName)
                        continue
                    }
                    operations.toolIntent(operationId, call, spec.argumentsJson, ToolReplayPolicy.forTool(tool, rawName), round)
                    loopDetector.recordIntent(rawName, args)
                    val writeRejection = writePaths?.let { subagentWriteScopeRejection(tool, args, it) }
                    val outcome = when {
                        tool == HarnessTool.SUBAGENT ->
                            ToolResult(UUID.randomUUID().toString(), now(), call.id, false, "子智能体 Lane 禁止再次派发子智能体")
                        writeRejection != null -> {
                            blockedWrites += subagentWriteTargetLabel(rawName, args)
                            ToolResult(UUID.randomUUID().toString(), now(), call.id, false, writeRejection)
                        }
                        else -> toolExecutor().execute(
                            call,
                            sessionId,
                            workspace,
                            allowApprovalRequest = false,
                            operationId = operationId,
                        )
                    }
                    if (outcome.approvalDeferred) {
                        deferredApprovals += SubagentApprovalHandoff(
                            toolName = rawName,
                            argumentsJson = spec.argumentsJson.take(MAX_HANDOFF_ARGS_CHARS),
                            reason = outcome.output.lineSequence().firstOrNull()?.take(200).orEmpty(),
                        )
                    } else if (writeRejection == null && tool in LANE_WRITE_TOOLS) {
                        // 被租约拦截或待审批的写入已分别记账，这里只追踪"真的执行了但失败"。
                        val target = subagentWriteTargetLabel(rawName, args)
                        if (outcome.success) {
                            failedWrites.remove(target)
                        } else {
                            failedWrites[target] = outcome.output.lineSequence().firstOrNull()?.take(200).orEmpty()
                        }
                    }
                    operations.toolSettled(operationId, outcome, round, toolName = rawName)
                    loopDetector.recordSettled(rawName, args, outcome.success, output = outcome.output)
                }
            }
            operations.finish(sessionId, "failed", details = "max rounds", laneName = laneName)
            SubagentLaneResult(
                success = false,
                summary = buildString {
                    append("⚠️ 子智能体用尽 $toolRounds 轮工具预算仍未收束，未产出最终结论。")
                    if (finalText.isNotBlank()) {
                        append("\n\n最后阶段输出：\n")
                        append(finalText)
                    }
                },
                toolCallCount = toolCalls,
                termination = SubagentTermination.MAX_ROUNDS,
                pendingApprovals = deferredApprovals.toList(),
                blockedWrites = blockedWrites.toList(),
            )
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // 结构化取消（用户停止或编排层超时）必须向上重抛；
            // 否则 lane operation 永远停留在 RUNNING，形成僵尸行。
            // finish 自身是挂起点，需在 NonCancellable 下落盘（与主循环清理链同一模式）。
            withContext(kotlinx.coroutines.NonCancellable) {
                operations.finish(sessionId, "aborted", details = "已取消", laneName = laneName)
            }
            throw cancellation
        } catch (throwable: Throwable) {
            operations.finish(sessionId, "failed", details = throwable.message, laneName = laneName)
            SubagentLaneResult(
                success = false,
                summary = throwable.message ?: "子智能体执行失败",
                toolCallCount = toolCalls,
                termination = SubagentTermination.FAILED,
                pendingApprovals = deferredApprovals.toList(),
                blockedWrites = blockedWrites.toList(),
            )
        }
    }

    /**
     * Lane 历史的 token 预算。子循环没有主循环那套压缩管线，只做"够用就不动、超了才收紧"，
     * 并给系统提示词、工具 schema 与本轮输出留出余量。
     */
    private suspend fun laneHistoryBudget(model: top.wkbin.tianxuan.harness.ModelConfig): Int {
        val budget = ContextWindowPolicy.clampedBudget(
            model.contextTokens,
            runCatching { settingsDataStore.contextBudgetTokens.first() }.getOrDefault(DEFAULT_CONTEXT_BUDGET_TOKENS),
            modelId = model.model,
            providerId = model.provider,
        )
        return (budget * LANE_HISTORY_BUDGET_FRACTION).toInt().coerceAtLeast(MIN_LANE_HISTORY_TOKENS)
    }

    private suspend fun providerMessages(
        sessionId: String,
        laneName: String,
        forceFinalAnswer: Boolean,
        model: top.wkbin.tianxuan.harness.ModelConfig,
        historyBudgetTokens: Int,
    ): List<ApiMessage> {
        val toolCallMode = if (model.pureChatMode) ToolCallMode.DISABLED else model.toolCallMode
        return isolatedProviderMessages(
            messages = treeStore.load(sessionId, laneName),
            systemPrompt = laneSystemPrompt(toolCallMode),
            forceFinalAnswer = forceFinalAnswer,
            toolCallMode = toolCallMode,
            historyBudgetTokens = historyBudgetTokens,
        )
    }

    /**
     * JSON 文本模式下工具调用协议不在原生字段里：ProviderClient 只会把工具的 JSON 定义追加到
     * system 消息，输出格式规则来自主循环的 SystemPromptBuilder。子 Lane 不走那条链路，
     * 因此必须自己带上标记格式说明，否则模型拿到工具清单却不知道怎么发起调用。
     */
    private fun laneSystemPrompt(toolCallMode: ToolCallMode): String = buildString {
        append(context.getString(R.string.harness_prompt_subagent_lane_system))
        if (toolCallMode == ToolCallMode.JSON_TEXT) {
            runCatching { promptAssets.render("prompts/tool_call_json.md") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { append("\n\n").append(it) }
        }
    }

    private suspend fun chatWithRetry(
        model: top.wkbin.tianxuan.harness.ModelConfig,
        messages: List<ApiMessage>,
        text: StringBuilder,
    ): top.wkbin.tianxuan.harness.ChatResult {
        var lastFailure: IOException? = null
        repeat(NETWORK_ATTEMPTS) { attempt ->
            try {
                return providerClient.chatStream(model, messages, onReasoning = {}) { text.append(it) }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (failure: IOException) {
                lastFailure = failure
                text.clear()
                if (attempt + 1 < NETWORK_ATTEMPTS) delay(NETWORK_RETRY_DELAY_MS)
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val DEFAULT_MAX_ROUNDS = 20
        private const val MIN_MAX_ROUNDS = 5

        /**
         * 工具轮数上限。原为 30，复杂编码类子任务会被强行截断在中途；
         * 真正的兜底是编排层超时，轮数上限只用来拦住"漫游式只读遍历"。
         */
        private const val MAX_MAX_ROUNDS = 60
        private const val NETWORK_ATTEMPTS = 2
        private const val NETWORK_RETRY_DELAY_MS = 1_000L
        private const val DEFAULT_CONTEXT_BUDGET_TOKENS = 128_000
        private const val LANE_HISTORY_BUDGET_FRACTION = 0.55
        private const val MIN_LANE_HISTORY_TOKENS = 4_000
        private const val MAX_HANDOFF_ARGS_CHARS = 2_000
    }
}

/**
 * 未被接受为完成时的汇总正文：先说清为什么没完成，再交出可操作的交接信息，
 * 最后附上子智能体的原始输出。父智能体据此能直接接手，而不是只看到一句"失败"。
 */
internal fun laneSummary(
    verdict: SubagentConclusionVerdict,
    roundText: String,
    deferredApprovals: List<SubagentApprovalHandoff>,
    blockedWrites: List<String>,
    failedWrites: Map<String, String> = emptyMap(),
): String {
    if (verdict.accepted) return roundText
    return buildString {
        appendLine("⚠️ 子任务未确认完成：${verdict.reason}")
        if (deferredApprovals.isNotEmpty()) {
            appendLine()
            appendLine("**需要主智能体在主会话重新发起并完成审批的调用**（后台 Lane 无法暂停等待审批）：")
            deferredApprovals.forEach { handoff ->
                appendLine("- `${handoff.toolName}` 参数：${handoff.argumentsJson}")
            }
        }
        if (blockedWrites.isNotEmpty()) {
            appendLine()
            appendLine("**被写租约拦截、实际未落盘的写入**：${blockedWrites.joinToString("、")}")
            appendLine("重新派发时请为该子任务声明 write_paths，或由主智能体自行写入。")
        }
        if (failedWrites.isNotEmpty()) {
            appendLine()
            appendLine("**尝试写入但最终失败的目标**（这些文件并不存在或未更新）：")
            failedWrites.forEach { (target, reason) -> appendLine("- $target → $reason") }
        }
        if (roundText.isNotBlank()) {
            appendLine()
            appendLine("子智能体最后输出：")
            append(roundText)
        }
    }.trim()
}

/**
 * Lane 请求消息组装。
 *
 * 与主循环的 ApiContextAssembler 对齐两件事：
 * - JSON_TEXT 模式必须把 tool_call / tool_result 转成文本形态（这类接口不认识 tool 角色，
 *   原生形态会让"首轮正常、执行一次工具后下一轮 400"）；
 * - 历史按 token 预算收紧，避免几次大文件读取就把子循环上下文撑满。
 */
internal fun isolatedProviderMessages(
    messages: List<HarnessMessage>,
    systemPrompt: String,
    forceFinalAnswer: Boolean,
    toolCallMode: ToolCallMode = ToolCallMode.NATIVE,
    historyBudgetTokens: Int = 0,
): List<ApiMessage> = buildList {
    val finalInstruction = if (forceFinalAnswer) {
        "\n这是最后一轮。禁止继续调用工具，请根据已有结果直接输出结论；信息不完整时明确说明限制。"
    } else {
        ""
    }
    add(ApiMessage(role = "system", content = systemPrompt + finalInstruction))
    val taskStart = messages.indexOfLast { it is top.wkbin.tianxuan.harness.UserMessage }
        .takeIf { it >= 0 } ?: messages.size
    val task = budgetedLaneMessages(messages.drop(taskStart), historyBudgetTokens)
    val toolNames = task.filterIsInstance<ToolCall>()
        .associate { it.id to (it.rawToolName ?: HarnessApiMapper.apiName(it.tool)) }
    val answeredIds = task.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }
    task.forEach { message ->
        when {
            message is CapabilityEvent || message is ModelSwitchEvent || message is SkillSuggestion -> Unit
            // 文本模式：落库的 assistant 文本已剥离工具标记，调用意图必须以同一协议形态
            // 回放成 assistant 消息，否则模型看不到自己调用了什么参数，结果无法关联；
            // 悬空调用（无结果）不回放，与主会话 NATIVE 分支同口径。结果以 user 文本回灌。
            toolCallMode == ToolCallMode.JSON_TEXT && message is ToolCall -> {
                if (message.id in answeredIds) {
                    add(
                        ApiMessage(
                            role = "assistant",
                            content = TextToolCallCodec.encodeCall(
                                toolNames[message.id] ?: HarnessApiMapper.apiName(message.tool),
                                message.args.toString(),
                            ),
                        ),
                    )
                }
            }
            toolCallMode == ToolCallMode.JSON_TEXT && message is ToolResult -> add(
                ApiMessage(
                    role = "user",
                    content = "【工具 ${toolNames[message.toolCallId] ?: "工具"} 执行结果·" +
                        "${if (message.success) "成功" else "失败"}】\n${message.output}",
                ),
            )
            else -> add(HarnessApiMapper.toApiMessage(message))
        }
    }
}

internal fun isDirectSubagentConclusion(
    assistantText: String,
    structuredCalls: List<top.wkbin.tianxuan.harness.ApiToolCallSpec>,
    textNormalization: TextToolCallCodec.Normalization,
): Boolean = assistantText.isNotBlank() && structuredCalls.isEmpty() && !textNormalization.hasMarkers

internal fun shouldForceSubagentFinalAnswer(round: Int, maxRounds: Int): Boolean =
    round >= maxRounds.coerceAtLeast(1) - 1
