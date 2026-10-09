package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.harness.metrics.RunMetrics
import top.wkbin.tianxuan.harness.session.SessionTreeStore
import top.wkbin.tianxuan.harness.compaction.CompactionManager
import top.wkbin.tianxuan.harness.effects.RetryPolicy
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import top.wkbin.tianxuan.harness.events.CapabilityEventWriter
import top.wkbin.tianxuan.harness.projection.SessionMessageProjector
import top.wkbin.tianxuan.harness.projection.SessionStateMirrors
import top.wkbin.tianxuan.harness.session.ApiContextAssembler
import kotlin.time.Duration.Companion.milliseconds

/**
 * 本地流式增量处理（消息投影 / 持久化 / UI 派生）失败。
 *
 * 与网络 [IOException] 严格隔离：这类异常不是网络故障，绝不能触发整轮网络重发，
 * 否则会把半截内容、重复文字或残缺 JSON 污染到后续上下文。
 */
internal class StreamChunkHandlingException(message: String, cause: Throwable) : Exception(message, cause)

/** 包裹流式回调：本地处理异常统一转成 [StreamChunkHandlingException]，取消原样透传。 */
internal inline fun <T> withinStreamHandling(block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    throw StreamChunkHandlingException(throwable.message ?: "流式增量处理失败", throwable)
}

/** 模型能力选择、流式请求重试及助手回复持久化；不持有会话调度状态。 */
class HarnessProviderRunner(
    private val providerClient: ProviderClient,
    private val messageStore: SessionTreeStore,
    private val operationCoordinator: OperationCoordinator,
    private val stateMirrors: SessionStateMirrors,
    private val messageProjector: SessionMessageProjector,
    private val capabilityWriter: CapabilityEventWriter,
    private val agentEventLogger: AgentEventLogger,
    private val contextAssembler: ApiContextAssembler,
    private val compactionManager: CompactionManager,
) {
    /**
     * 记录本轮 @提及 的能力挂载事件（UI 展示用）。
     *
     * prefix-cache 稳定性（use_capability 第一步，稳定 provider 面）：
     * @提及 **不再裁剪** provider 可见的 tools 数组——原实现在有提及的轮次把数组裁到
     * 被提及的 server、下一轮恢复全集，两次字节漂移都会击穿整个前缀缓存；而被击穿
     * 重新计费的代价（全前缀 × 全价）远大于保留全集 schema 的增量 token。提及只产生
     * 能力事件记录，工具可用性由 MCP server 的启用/连接状态决定。
     */
    suspend fun resolveEffectiveModel(sessId: String, model: ModelConfig): ModelConfig {
        val msgs = messageProjector.messagesFlow(sessId).value
        val latestUserMessage = msgs.filterIsInstance<UserMessage>().lastOrNull()
        val mentionedNames = MentionExtractor.parse(latestUserMessage?.text.orEmpty())
        capabilityWriter.writeIfMentioned(sessId, latestUserMessage?.id.orEmpty(), mentionedNames, model)
        return model
    }

    /** 流式调用 + 限流/网络退避重试。恢复不了的失败以 Failed 终态返回；取消与超过重试上限的原样抛出 */
    suspend fun callProviderWithRetry(
        sessId: String,
        model: ModelConfig,
        sessionEntity: HarnessSessionEntity?,
        sessionWorkspace: String,
        operationId: String,
        assistantId: String,
        assistantAt: Long,
        round: Int,
        startedAt: Long,
        retryPolicy: RetryPolicy,
        metrics: RunMetrics,
    ): TurnProviderOutcome {
        val streamText = StreamBuffer()
        val streamReasoning = StreamBuffer(maxChars = ProviderClient.MAX_STREAM_REASONING_CHARS)
        var streamed: ChatResult? = null
        // 干净快照基线：每次网络重发前彻底清空本轮缓冲区并删除流式气泡，
        // 避免半截字符/残缺 JSON 拼接到下一次重试的增量之后污染上下文。
        fun resetStreamBaseline() {
            streamText.clear()
            streamReasoning.clear()
            messageProjector.remove(sessId, assistantId)
        }
        suspend fun settleFailedStream() {
            stateMirrors.setThinkingLive(sessId, false)
            if (streamText.length > 0) {
                persistAssistant(
                    sessId, assistantId, assistantAt, streamText.toString(),
                    streamReasoning.toString().ifBlank { null },
                    totalMs = now() - startedAt, operationId = operationId, round = round,
                )
            } else {
                messageProjector.remove(sessId, assistantId)
            }
        }

        var requestModel = model
        var outputBudgetReduced = false
        var netRetry = 0
        suspend fun assembleFor(requestModel: ModelConfig) = contextAssembler.assemble(
            sessId = sessId,
            model = requestModel,
            workspacePath = sessionWorkspace,
            projectTypeOverride = sessionEntity?.projectType.orEmpty(),
            thinkingMode = stateMirrors.requestThinkingMode(sessId),
            sessionRunMode = sessionEntity?.runMode,
        )
        fun estimateTokens(messages: List<ApiMessage>) = messages.sumOf { message ->
            ContextWindowPolicy.estimateTokens(message.content.orEmpty()) +
                ContextWindowPolicy.estimateTokens(message.reasoning_content.orEmpty()) +
                message.tool_calls.orEmpty().sumOf { call ->
                    ContextWindowPolicy.estimateTokens(call.function.name) +
                        ContextWindowPolicy.estimateTokens(call.function.arguments)
                } +
                message.imageUrls.size * ContextWindowPolicy.ESTIMATED_IMAGE_TOKENS
        }
        // Context and prompt remain immutable during network retries. The configured model
        // window is authoritative: a transport heuristic must never persistently compact a
        // valid 128k/200k conversation down to 64k.
        var requestMessages = assembleFor(model)
        var imageStripped = false
        var overflowRecovered = false
        // 与请求构造同口径：messages + provider 可见 tools schema（仅 NATIVE 模式独立于 messages）。
        // 漏掉 schema 会低估真实输入规模，余量被吃光后以 400/413 溢出。
        val toolSchemaTokens =
            if (!model.pureChatMode && model.toolCallMode == ToolCallMode.NATIVE) {
                ContextWindowPolicy.estimateToolDefinitionTokens(ProviderClient.buildDynamicTools())
            } else {
                0
            }
        val estimatedRequestTokens = estimateTokens(requestMessages) + toolSchemaTokens
        val maxNetworkRetries = maxNetworkRetriesFor(estimatedRequestTokens, retryPolicy.maxRetries)
        val maxAttempts = maxNetworkRetries + 1
        if (maxNetworkRetries < retryPolicy.maxRetries) {
            agentEventLogger.log(
                sessId,
                "LargeContextRetryPolicy",
                "估算输入约 $estimatedRequestTokens tokens，大上下文网络重试限制为 $maxNetworkRetries 次",
            )
        }
        while (streamed == null) {
            try {
                stateMirrors.setStatus(sessId, "等待模型首个响应（${netRetry + 1}/$maxAttempts）")
                operationCoordinator.providerIntent(
                    operationId = operationId,
                    effectId = assistantId,
                    round = round,
                    attempt = netRetry + 1,
                    maxAttempts = maxAttempts,
                )
                streamed = providerClient.chatStream(
                    requestModel,
                    requestMessages,
                    onReasoning = { chunk ->
                        withinStreamHandling {
                            streamReasoning.append(chunk)
                            stateMirrors.setThinkingLive(sessId, true)
                            stateMirrors.recordThinkingObserved(sessId)
                            streamReasoning.publishIfDue(now(), ProviderClient.STREAM_PUBLISH_INTERVAL_MS)?.let {
                                messageProjector.streamReasoning(sessId, assistantId, assistantAt, it)
                            }
                        }
                    },
                    onToolProgress = { progress ->
                        withinStreamHandling {
                            stateMirrors.setThinkingLive(sessId, false)
                            stateMirrors.setStatus(
                                sessId,
                                if (progress.name == "write") {
                                    "正在生成 write · +${progress.addedLines}"
                                } else {
                                    "正在生成 edit · +${progress.addedLines} -${progress.deletedLines}"
                                },
                            )
                        }
                    },
                ) { chunk ->
                    withinStreamHandling {
                        stateMirrors.setStatus(sessId, "回复中")
                        streamText.append(chunk)
                        streamText.publishIfDue(now(), ProviderClient.STREAM_PUBLISH_INTERVAL_MS)?.let {
                            messageProjector.streamText(sessId, assistantId, assistantAt, it)
                        }
                    }
                }
                // 流式传输完毕，无条件刷新一次完整内容
                withinStreamHandling {
                    if (streamReasoning.length > 0) {
                        messageProjector.streamReasoning(sessId, assistantId, assistantAt, streamReasoning.toString())
                    }
                    if (streamText.length > 0) {
                        messageProjector.streamText(sessId, assistantId, assistantAt, streamText.toString())
                    }
                }
            } catch (cancellation: CancellationException) {
                persistCancelledPartial(sessId, assistantId, assistantAt, streamText, streamReasoning, startedAt, operationId, round)
                withContext(NonCancellable) {
                    agentEventLogger.log(sessId, "Cancelled", "用户主动取消执行，保留已生成内容 ${streamText.length} 字符")
                }
                throw cancellation
            } catch (handling: StreamChunkHandlingException) {
                // 本地流式处理异常：原样终止，严禁当作网络故障重发（否则重复文字/半截 JSON 会污染上下文）。
                stateMirrors.setThinkingLive(sessId, false)
                agentEventLogger.log(
                    sessId,
                    "StreamChunkHandling",
                    "流式增量处理失败（本地异常，禁止网络重发）：${handling.message}",
                    handling,
                )
                if (streamText.length > 0) {
                    persistAssistant(
                        sessId,
                        assistantId,
                        assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                } else {
                    messageProjector.remove(sessId, assistantId)
                }
                return TurnProviderOutcome.Failed(
                    "流式响应处理失败（本地异常，未自动重试）：${friendly(handling.cause ?: handling)}",
                )
            } catch (rateLimit: LlmRateLimitException) {
                currentCoroutineContext().ensureActive()
                if (rateLimit.quotaExhausted) {
                    stateMirrors.setThinkingLive(sessId, false)
                    agentEventLogger.log(sessId, "QuotaExhausted", rateLimit.message.orEmpty(), rateLimit)
                    // 移除空的流式气泡；错误通过 error state 展示，不写入消息历史，避免下一轮注入模型上下文
                    messageProjector.remove(sessId, assistantId)
                    val detail = rateLimit.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
                    return TurnProviderOutcome.Failed("模型服务商额度已耗尽，无法继续执行。请充值、切换可用模型或更新 API Key。$detail")
                }
                netRetry++
                if (netRetry > maxNetworkRetries) {
                    settleFailedStream()
                    throw rateLimit
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                val waitSeconds = rateLimit.retryAfterSeconds ?: (netRetry * RETRY_BACKOFF_SEC).coerceAtMost(60L)
                stateMirrors.setStatus(sessId, "请求受限，${waitSeconds} 秒后自动重试（$netRetry/$maxNetworkRetries）")
                agentEventLogger.log(sessId, "RateLimitRetry", "限流退避 ${waitSeconds}s，重试 $netRetry/$maxNetworkRetries", rateLimit)
                resetStreamBaseline()
                for (remaining in waitSeconds downTo 1L) {
                    currentCoroutineContext().ensureActive()
                    stateMirrors.setStatus(sessId, "请求受限，${remaining} 秒后自动重试（$netRetry/$maxNetworkRetries）")
                    delay(1000L.milliseconds)
                }
            } catch (invalidOutputTokens: LlmInvalidOutputTokensException) {
                currentCoroutineContext().ensureActive()
                if (outputBudgetReduced) throw invalidOutputTokens
                outputBudgetReduced = true
                val current = ContextWindowPolicy.normalizeOutputTokens(model.maxTokens, 8_192)
                val reduced = (current / 2).coerceAtLeast(1)
                requestModel = model.copy(maxTokens = reduced)
                agentEventLogger.log(
                    sessId,
                    "OutputBudgetReduction",
                    "Provider 拒绝输出预算，严格降额至 $reduced 后重试",
                    invalidOutputTokens,
                )
                resetStreamBaseline()
            } catch (contextOverflow: LlmContextOverflowException) {
                currentCoroutineContext().ensureActive()
                stateMirrors.setThinkingLive(sessId, false)
                // 必须位于 IOException 之前：LlmContextOverflowException 本身继承 IOException。
                // 若先进入通用网络重试分支，413 / context_length_exceeded 只会原样重发，
                // 永远到不了 overflow -> compact -> replay 的自愈闭环。
                if (!overflowRecovered) {
                    overflowRecovered = true
                    stateMirrors.setStatus(sessId, "上下文超限，正在紧急压缩历史后重试")
                    agentEventLogger.log(
                        sessId,
                        "ContextOverflowRecovery",
                        "识别到上下文超限，执行紧急压缩：${contextOverflow.message}",
                        contextOverflow,
                    )
                    val recovered = recoverFromContextOverflow(sessId, model)
                    if (recovered != null) {
                        agentEventLogger.log(
                            sessId,
                            "ContextOverflowRecovery",
                            "紧急压缩完成：折叠 ${recovered.first} 条，保留 ${recovered.second} 条，重新组装请求重试",
                        )
                        resetStreamBaseline()
                        requestMessages = assembleFor(requestModel)
                        continue
                    }
                }
                val pendingImages = requestMessages.sumOf { it.imageUrls.size }
                if (!imageStripped && pendingImages > 0) {
                    imageStripped = true
                    // 故意不调 assembleFor 重算：走到这里说明紧急压缩已失败/无效（持久层消息仍带图片），
                    // 重新组装会把图片原样带回，立即再次超限。内存剥离是唯一能真正降低请求体积的手段
                    requestMessages = requestMessages.map { it.copy(imageUrls = emptyList()) }
                    agentEventLogger.log(
                        sessId,
                        "ContextOverflowImageStrip",
                        "紧急压缩后仍超限，剥离 $pendingImages 张图片降级重试",
                        contextOverflow,
                    )
                    resetStreamBaseline()
                    continue
                }
                // 与 ModelError 路径一致：已流式产出的半截内容先落库保留，避免静默丢弃
                if (streamText.length > 0) {
                    persistAssistant(
                        sessId,
                        assistantId,
                        assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                } else {
                    messageProjector.remove(sessId, assistantId)
                }
                agentEventLogger.log(
                    sessId,
                    "ContextOverflowUnrecoverable",
                    "上下文超限且压缩/剥离图片均无法恢复",
                    contextOverflow,
                )
                return TurnProviderOutcome.Failed(
                    "上下文超出模型窗口，自动压缩后仍无法恢复。" +
                        "可以让模型用 compress 工具手动压缩，或切换到更大上下文窗口的模型后重试。",
                )
            } catch (empty: LlmEmptyResponseException) {
                // 必须位于 IOException 之前：LlmEmptyResponseException 继承 IOException。
                // 空响应多为上游瞬时异常（网关提前断流、配额抖动），原样重发通常即可恢复；
                // 超出预算则明确失败并给出原始响应首部——绝不返回"什么都没有"，
                // 否则上层会走"无工具调用 → 收尾 → Outcome=completed"，前台零提示。
                currentCoroutineContext().ensureActive()
                netRetry++
                if (netRetry > EMPTY_RESPONSE_MAX_RETRIES) {
                    stateMirrors.setThinkingLive(sessId, false)
                    messageProjector.remove(sessId, assistantId)
                    agentEventLogger.log(sessId, "EmptyModelResponse", empty.message.orEmpty(), empty)
                    // 文案已按长度受控（正文 + 截断首部），此处不套 friendly() 的 200 字截断，
                    // 否则尾部的原始响应首部会被整段砍掉，等于失去唯一线索。
                    return TurnProviderOutcome.Failed(empty.message.orEmpty().ifBlank { ProviderClient.EMPTY_RESPONSE_MESSAGE })
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                stateMirrors.setStatus(sessId, "模型返回空响应，正在重试（$netRetry/$EMPTY_RESPONSE_MAX_RETRIES）")
                agentEventLogger.log(sessId, "EmptyModelResponseRetry", "第 $netRetry 次空响应，重试上限 $EMPTY_RESPONSE_MAX_RETRIES")
                resetStreamBaseline()
                delay(retryPolicy.delayForRetry(netRetry).milliseconds)
            } catch (io: IOException) {
                // 用户取消会主动关闭 socket，通常以 IOException 形式抛出：先按取消语义保留已生成内容，再传播取消。
                if (!currentCoroutineContext().isActive) {
                    persistCancelledPartial(sessId, assistantId, assistantAt, streamText, streamReasoning, startedAt, operationId, round)
                    withContext(NonCancellable) {
                        agentEventLogger.log(sessId, "Cancelled", "用户主动取消执行，保留已生成内容 ${streamText.length} 字符")
                    }
                }
                currentCoroutineContext().ensureActive()
                netRetry++
                // 瞬态故障（断线 / 超时 / TLS 中断 / 上游 5xx）与请求体大小、上下文规模无关，
                // 至少保留 TRANSIENT_MAX_RETRIES 次重试，不被大上下文降级压到 1 次——
                // 否则长会话一次 503（如 Cloudflare 524）就会让整轮失败，用户只能手动接续。
                val transient = isTransientFailure(io)
                val retryBudget = effectiveRetryBudget(maxNetworkRetries, io)
                // netRetry 表示「这是第几次失败」；净重试预算为 retryBudget 次，故第 retryBudget+1 次失败即放弃。
                // 原写法 "重试 $netRetry/$retryBudget" 会被误读成「已执行第 retryBudget 次重试、仍在继续」。
                agentEventLogger.log(
                    sessId,
                    "NetworkRetry",
                    "网络中断，第 $netRetry 次失败（重试上限 $retryBudget 次" +
                        (if (transient) "，瞬态故障不受大上下文降级" else "") + "）：${io.message}",
                    io,
                )
                if (netRetry > retryBudget) {
                    settleFailedStream()
                    throw io
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                stateMirrors.setStatus(sessId, "网络中断，自动重发中（第 $netRetry 次失败，上限 $retryBudget）")
                resetStreamBaseline()
                delay(retryPolicy.delayForRetry(netRetry).milliseconds)
            } catch (throwable: Throwable) {
                stateMirrors.setThinkingLive(sessId, false)
                // 模型不支持图片输入（HTTP 400）时，剥离全部图片降级重试一次，避免整轮中断
                val lowerMsg = throwable.message.orEmpty().lowercase()
                val pendingImages = requestMessages.sumOf { it.imageUrls.size }
                if (!imageStripped && pendingImages > 0 &&
                    ("do not support image" in lowerMsg || "does not support image" in lowerMsg ||
                        "image input" in lowerMsg || "supports image" in lowerMsg ||
                        "image not supported" in lowerMsg ||
                        "不支持图片" in lowerMsg || "不支持图像" in lowerMsg ||
                        "图片输入" in lowerMsg || "图像输入" in lowerMsg)
                ) {
                    imageStripped = true
                    requestMessages = requestMessages.map { it.copy(imageUrls = emptyList()) }
                    agentEventLogger.log(
                        sessId, "VisionFallback",
                        "模型不支持图片输入，已剥离 $pendingImages 张图片降级重试", throwable,
                    )
                    resetStreamBaseline()
                    continue
                }
                agentEventLogger.log(sessId, "ModelError", "LLM 调用失败: ${throwable.message}", throwable)
                if (streamText.length > 0) {
                    persistAssistant(
                        sessId,
                        assistantId,
                        assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                } else {
                    // 移除空的流式气泡；错误通过 error state 展示，不写入消息历史
                    messageProjector.remove(sessId, assistantId)
                }
                return TurnProviderOutcome.Failed(friendly(throwable))
            }
        }
        // 仅在本轮请求真正成功后记录估算值，与 HarnessLoop 里 recordUsage 的计量轮次对齐
        metrics.recordEstimatedInput(estimatedRequestTokens)
        messageProjector.endStreaming(sessId)
        return TurnProviderOutcome.Success(streamed, streamText.toString())
    }

    /**
     * 紧急压缩：把历史折到正常预算的一小部分（返回 (折叠条数, 保留条数)）。
     * 机械摘要（model=null）——摘要请求若 cache-replay 同一前缀，会以同样方式超限，
     * 恢复路径上绝不能再调 LLM。无可折叠内容（历史已是最小保留态 / 分支漂移放弃折叠）
     * 返回 null，由调用方走下一个恢复杠杆。
     */
    private suspend fun recoverFromContextOverflow(sessId: String, model: ModelConfig): Pair<Int, Int>? {
        return try {
            val context = compactionManager.project(sessId)
            if (context.messages.size <= 1) return null
            val keepFrom = ContextWindowPolicy.computeKeepFromIndex(
                context.messages,
                emergencyFoldBudget(model),
                systemTokens = 0,
                reserveTokens = 0,
                // 紧急折叠仍按真实请求口径预留 schema：不外发 tools 时预留 0，折得更狠
                toolSchemaReserveTokens = ContextWindowPolicy.toolSchemaReserveTokensFor(
                    pureChat = model.pureChatMode,
                    toolDisabled = model.toolCallMode == ToolCallMode.DISABLED,
                ),
            )
            if (keepFrom < 1) return null
            val compacted = compactionManager.compact(sessId, context, keepFrom, model = null)
            val folded = context.messages.size - compacted.messages.size
            if (folded <= 0) return null
            folded to compacted.messages.size
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            agentEventLogger.log(sessId, "ContextOverflowRecoveryFailed", "紧急压缩失败：${throwable.message}", throwable)
            null
        }
    }

    /** 回合结束后落库助手回复；无文本时只结算 usage 记录 */
    suspend fun persistAssistantOutput(
        sessId: String,
        assistantId: String,
        assistantAt: Long,
        round: Int,
        startedAt: Long,
        operationId: String,
        result: ChatResult,
        effectiveModel: ModelConfig,
        displayText: String,
        hasToolCalls: Boolean,
    ) {
        if (displayText.isNotEmpty()) {
            persistAssistant(
                sessId,
                assistantId,
                assistantAt,
                displayText,
                result.reasoningContent,
                totalMs = if (!hasToolCalls) now() - startedAt else null,
                reasoningMs = result.reasoningMs,
                operationId = operationId,
                round = round,
                usage = result.usage,
                model = effectiveModel,
            )
        } else {
            val usageEntity = result.usage.takeIf { it.hasData }?.let {
                operationCoordinator.usageEntity(
                    sessionId = sessId,
                    operationId = operationId,
                    entryId = null,
                    provider = effectiveModel.provider,
                    modelId = effectiveModel.model,
                    usage = it,
                )
            }
            operationCoordinator.providerSettled(operationId, null, usage = usageEntity, round = round)
        }
    }

    private suspend fun persistAssistant(
        sessId: String,
        id: String,
        createdAt: Long,
        text: String,
        reasoning: String? = null,
        totalMs: Long? = null,
        reasoningMs: Long? = null,
        operationId: String? = null,
        round: Int = 0,
        usage: ChatUsage? = null,
        model: ModelConfig? = null,
    ) {
        val message = AssistantText(
            id = id,
            createdAt = createdAt,
            text = text,
            reasoning = reasoning,
            totalMs = totalMs,
            reasoningMs = reasoningMs,
            modelId = model?.model,
            providerId = model?.provider,
            promptTokens = usage?.inputTokens?.takeIf { it > 0 }?.toInt(),
            completionTokens = usage?.outputTokens?.takeIf { it > 0 }?.toInt(),
            cachedTokens = usage?.cacheReadTokens?.takeIf { it > 0 }?.toInt(),
        )
        if (operationId != null) {
            val usageEntity = usage?.takeIf { it.hasData }?.let {
                operationCoordinator.usageEntity(
                    sessionId = sessId,
                    operationId = operationId,
                    entryId = id,
                    provider = model?.provider,
                    modelId = model?.model,
                    usage = it,
                )
            }
            operationCoordinator.providerSettled(operationId, message, usage = usageEntity, round = round)
        } else {
            messageStore.append(sessId, message)
        }
        messageProjector.publishPersisted(sessId, message)
    }

    /** 用户主动停止时保留已流式内容；协程已取消，落库需 NonCancellable，失败退回删除半截气泡。 */
    private suspend fun persistCancelledPartial(
        sessId: String,
        assistantId: String,
        assistantAt: Long,
        streamText: StreamBuffer,
        streamReasoning: StreamBuffer,
        startedAt: Long,
        operationId: String,
        round: Int,
    ) {
        val keptChars = streamText.length
        stateMirrors.setThinkingLive(sessId, false)
        withContext(NonCancellable) {
            if (keptChars > 0) {
                runCatching {
                    persistAssistant(
                        sessId, assistantId, assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                }.onFailure { messageProjector.remove(sessId, assistantId) }
            } else {
                messageProjector.remove(sessId, assistantId)
            }
        }
    }
    private fun now(): Long = System.currentTimeMillis()
    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    companion object {
        private const val LARGE_REQUEST_TOKEN_THRESHOLD = 64_000
        private const val LARGE_REQUEST_MAX_RETRIES = 1

        /** 瞬态故障（断线 / 5xx 等）的保底重试次数，不受大上下文降级影响。 */
        private const val TRANSIENT_MAX_RETRIES = 3

        /**
         * 空响应（HTTP 200 但无正文/推理/工具调用）的重发上限。
         * 单次空响应多数是上游瞬时抖动，重发即可恢复；连续多次仍是空则判定为模型/网关
         * 侧问题，明确失败并附上原始响应首部，而不是让用户面对"没有任何回复"。
         */
        private const val EMPTY_RESPONSE_MAX_RETRIES = 2

        /** 紧急压缩目标：把历史折到正常预算的 25%（computeKeepFromIndex 内部再扣输出/schema 预留）。 */
        private const val EMERGENCY_FOLD_RATIO_PERCENT = 25
        private const val EMERGENCY_FOLD_MIN_BUDGET = 8_000
        private const val DEFAULT_CONTEXT_BUDGET_TOKENS = 128_000

        internal fun emergencyFoldBudget(model: ModelConfig): Int =
            (ContextWindowPolicy.clampedBudget(
                model.contextTokens,
                DEFAULT_CONTEXT_BUDGET_TOKENS,
                modelId = model.model,
                providerId = model.provider,
            ) * EMERGENCY_FOLD_RATIO_PERCENT / 100)
                .coerceAtLeast(EMERGENCY_FOLD_MIN_BUDGET)

        internal fun maxNetworkRetriesFor(estimatedRequestTokens: Int, configuredRetries: Int): Int =
            if (estimatedRequestTokens >= LARGE_REQUEST_TOKEN_THRESHOLD) {
                minOf(configuredRetries, LARGE_REQUEST_MAX_RETRIES)
            } else {
                configuredRetries
            }

        /**
         * 实际重试预算 = 大上下文降级后的预算，但瞬态故障（断线 / 超时 / TLS 中断 / 上游 5xx）
         * 至少保留 [TRANSIENT_MAX_RETRIES] 次，不被降级到 1 次。
         */
        internal fun effectiveRetryBudget(largeContextRetries: Int, throwable: Throwable): Int =
            if (isTransientFailure(throwable)) {
                maxOf(largeContextRetries, TRANSIENT_MAX_RETRIES)
            } else {
                largeContextRetries
            }

        /**
         * 判断是否为「原样重发同一请求即可安全恢复」的瞬态故障：连接被对端中止、读超时、
         * TLS 层中断、流意外结束，以及上游 5xx（[TransientHttpException]，如 Cloudflare 524 / 503）。
         *
         * 这些故障与请求体大小、上下文规模无关，因此不受大上下文重试降级影响（见 [effectiveRetryBudget]）；
         * 否则一次 503 就会让长会话整轮失败，用户只能手动接续。
         * 沿 cause 链最多上溯 10 层，避免自引用造成死循环。
         */
        internal fun isTransientFailure(throwable: Throwable): Boolean {
            var cause: Throwable? = throwable
            var depth = 0
            while (cause != null && depth < 10) {
                if (cause is java.net.SocketException ||
                    cause is java.io.InterruptedIOException ||
                    cause is javax.net.ssl.SSLException ||
                    cause is java.io.EOFException ||
                    cause is TransientHttpException
                ) {
                    return true
                }
                cause = cause.cause
                depth++
            }
            return false
        }

        const val RETRY_BACKOFF_MS = 1_000L
        const val RETRY_BACKOFF_SEC = 2L

    }
}
