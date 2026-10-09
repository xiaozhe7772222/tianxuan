package top.wkbin.tianxuan.harness.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import top.wkbin.tianxuan.core.database.AgentApprovalRepository
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.core.model.RunMode
import top.wkbin.tianxuan.harness.ApiMessage
import top.wkbin.tianxuan.harness.ContextWindowPolicy
import top.wkbin.tianxuan.harness.ImagePayloadCompressor
import top.wkbin.tianxuan.harness.HarnessApiMapper
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ModelConfig
import top.wkbin.tianxuan.harness.MentionExtractor
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolCallMode
import top.wkbin.tianxuan.harness.UserMessage
import top.wkbin.tianxuan.harness.compaction.CompactionManager
import top.wkbin.tianxuan.harness.compaction.SummaryRequestContext
import top.wkbin.tianxuan.harness.knowledge.KnowledgeManager
import top.wkbin.tianxuan.harness.prompt.MemoryRecallSelector
import top.wkbin.tianxuan.harness.prompt.SystemPromptBuilder

/**
 * API 请求上下文组装器：把会话实时消息投影成提供商协议消息列表。
 *
 * 从原 HarnessLoop.apiMessages 迁移而来，负责：
 * - 系统提示词注入（非纯净聊天模式；逐轮可变内容已全部外移，见下）
 * - 用户轮前缀块的持久化（recall_context entry：记忆召回 + 任务计划看板，每轮只算一次）
 * - 上下文压缩摘要的头部注入（预算驱动的滑动窗口折叠）
 * - NATIVE / JSON_TEXT 两种工具调用协议的消息形态转换（经 [ApiMessageProjector]）
 * - 视觉能力关闭时剥离图片输入
 *
 * Prefix cache 稳定性契约：system prompt 内不再含逐轮变化的内容（recall 已移到
 * user 轮后缀、路由规则块与技能按全会话累计），相邻两轮请求的 system 消息字节级一致；
 * 变化只出现在本轮新增的消息上（本就未进入缓存）。
 */
class ApiContextAssembler(
    private val compactionManager: CompactionManager,
    private val settingsDataStore: AgentPreferences,
    private val systemPromptBuilder: SystemPromptBuilder,
    private val sessionStore: SessionTreeStore,
    private val memoryRecallSelector: MemoryRecallSelector,
    private val agentApprovalRepository: AgentApprovalRepository,
    /** RAG 知识库管理器；为 null 时自动注入功能禁用（向后兼容）。 */
    private val knowledgeManager: KnowledgeManager? = null,
) {
    suspend fun assemble(
        sessId: String,
        model: ModelConfig,
        workspacePath: String,
        projectTypeOverride: String = "",
        thinkingMode: Boolean = false,
        sessionRunMode: String? = null,
    ): List<ApiMessage> {
        val compactionEnabled = runCatching { settingsDataStore.contextCompactionEnabled.first() }.getOrDefault(true)
        // 「历史折叠线比例」：让历史在预算的一部分处就开始折叠。
        // 与面板同源读取同一个偏好，保证两侧折叠决策一致。
        val foldingRatioPercent = runCatching { settingsDataStore.contextFoldingRatioPercent.first() }
            .getOrDefault(ContextWindowPolicy.DEFAULT_FOLDING_RATIO_PERCENT)
        // 与 SessionModelSwitcher 共用 clampedBudget：占用判定与实际请求必须是同一预算口径
        val budgetTokens = ContextWindowPolicy.clampedBudget(
            model.contextTokens,
            runCatching { settingsDataStore.contextBudgetTokens.first() }.getOrDefault(128_000),
            modelId = model.model,
            providerId = model.provider,
        )
        val toolCallMode = if (model.pureChatMode) ToolCallMode.DISABLED else model.toolCallMode
        // 折叠线的工具 schema 预留按模式取：不外发 tools 的会话不该白留这 5,600 token
        val toolSchemaReserveTokens = ContextWindowPolicy.toolSchemaReserveTokensFor(
            pureChat = model.pureChatMode,
            toolDisabled = toolCallMode == ToolCallMode.DISABLED,
        )

        var compactedContext = compactionManager.project(sessId)
        var msgs = compactedContext.messages

        // 用户轮前缀块（低权威背景资料：记忆召回 + 任务计划看板）：只对最新用户轮计算一次并
        // 持久化到会话树（appendRecallBlock 幂等），此后该轮的投影永远携带同一段字节。
        // 这取代了旧的「system prompt 尾部注入」——记忆召回与计划看板都是逐轮变化的内容，
        // 放在 system prompt 里会让整段前缀每轮/每步失效。
        // 持久化失败时放弃挂载：未持久化的字节进投影会导致下一轮组装漂移。
        if (!model.pureChatMode) {
            val latestUser = msgs.filterIsInstance<UserMessage>().lastOrNull()
            if (latestUser != null && latestUser.id !in compactedContext.recallBlocks) {
                val block = try {
                    memoryRecallSelector.turnPrefixBlock(
                        projectOwnerId = workspacePath.trim().trimEnd('/'),
                        sessionId = sessId,
                        userMessage = latestUser.text,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    ""
                }
                if (block.isNotBlank() && sessionStore.appendRecallBlock(sessId, latestUser.id, block)) {
                    compactedContext = compactedContext.copy(
                        recallBlocks = compactedContext.recallBlocks + (latestUser.id to block),
                    )
                }
            }
        }

        // 技能 @提及按全会话累计（一旦提及，规则常驻本会话）：避免「下一轮未提及→章节
        // 撤出」造成的 system prompt 漂移。MCP @ 提及已不裁剪 tools 数组也不动提示词
        // （只写能力事件，见 HarnessProviderRunner.resolveEffectiveModel）。
        val mentionedNames = msgs.filterIsInstance<UserMessage>()
            .flatMapTo(mutableSetOf()) { MentionExtractor.parse(it.text) }
        val userMessageTexts = msgs.filterIsInstance<UserMessage>().map { it.text }

        val rawSystemPrompt = if (!model.pureChatMode) {
            // 运行意图与 ToolExecutor 同口径解析：会话级优先，缺失才回落全局默认
            // （RunMode.fromId 对 null 返回 BUILD，因此必须先做 null 判别再回落）。
            val runMode = sessionRunMode?.let(RunMode::fromId)
                ?: runCatching { agentApprovalRepository.currentRunMode() }.getOrDefault(RunMode.BUILD)
            systemPromptBuilder.build(
                workspacePath,
                toolCallMode,
                mentionedNames,
                sessId,
                projectTypeOverride,
                userMessageTexts,
                runMode,
            )
        } else {
            ""
        }
        val systemPrompt = ContextWindowPolicy.fitSystemPrompt(rawSystemPrompt, budgetTokens)

        // 召回后缀的 token 计入预算占用：它们会随 user 消息进入 provider 请求
        val recallTokens = ContextWindowPolicy.estimateTokens(compactedContext.recallBlocks.values.joinToString("\n"))

        return buildList {
            if (systemPrompt.isNotEmpty()) {
                add(ApiMessage(role = "system", content = systemPrompt))
            }

            // RAG 知识库自动注入：在 system prompt 之后作为独立 system 消息
            // （不进 system prompt 本体，避免破坏 prefix cache 稳定性契约）。
            // 知识库为空或检索失败时静默跳过，对正常对话无影响。
            if (!model.pureChatMode && knowledgeManager != null) {
                val lastUserText = msgs.lastOrNull { it is UserMessage }?.let { (it as UserMessage).text }
                if (!lastUserText.isNullOrBlank() && lastUserText.length <= 8000) {
                    val ragContext = runCatching {
                        knowledgeManager.contextForPrompt(lastUserText, topK = 3)
                    }.getOrNull()
                    if (!ragContext.isNullOrBlank() && ragContext.length <= 8000) {
                        add(ApiMessage(role = "system", content = ragContext))
                    }
                }
            }

            // 老轮次工具结果截断（先于压缩判定）：预算线未越过时，历史轮的大输出（浏览器快照、
            // 长 read）仍会原样重复发送。最近若干条原样保留，更老的超过按工具阈值即压缩并附
            // history_read 指针——只影响发给 Provider 的正文，落库 transcript 与 UI 不变。
            // 关闭上下文压缩 = 用户要原始历史，此时同样不截断。
            // 顺序必须在压缩判定之前：只需截断即可回到预算线内的会话，不应再触发整段压缩
            // （一次额外 LLM 调用 + 历史永久降级为摘要）。
            if (compactionEnabled) {
                msgs = ContextWindowPolicy.truncateStaleToolResults(msgs, toolCallDetailsOf(msgs))
            }

            // 预算驱动的滑动窗口：从最近一轮往回累加 token，超出预算则更早的历史进入压缩态。
            // 是否裁剪原文只由真实 token 预算决定，不再按用户轮次阈值强制折叠。
            // 每模型压缩预算覆盖（pi 式 modelOverrides）：keepRecent 收紧 + reserve 预留。
            val computedKeepFromIndex = if (compactionEnabled) {
                ContextWindowPolicy.computeKeepFromIndex(
                    msgs,
                    budgetTokens,
                    ContextWindowPolicy.estimateTokens(systemPrompt) +
                        ContextWindowPolicy.estimateTokens(compactedContext.summaryLayer) +
                        recallTokens,
                    keepRecentTokens = model.compactionKeepRecentTokens ?: 0,
                    reserveTokens = model.compactionReserveTokens,
                    foldingRatioPercent = foldingRatioPercent,
                    toolSchemaReserveTokens = toolSchemaReserveTokens,
                )
            } else {
                0
            }
            if (computedKeepFromIndex > 0) {
                // LLM 结构化压缩摘要（pi 式）：当前模型生成，失败回退机械摘要。
                // summaryContext 让摘要请求重放主对话的 system + 摘要层 + 原始消息前缀，
                // 命中 provider KV 缓存（cache-replay 形状）。
                compactedContext = compactionManager.compact(
                    sessId,
                    compactedContext,
                    computedKeepFromIndex,
                    model = model,
                    summaryContext = SummaryRequestContext(
                        systemPrompt = systemPrompt,
                        summaryLayer = compactedContext.summaryLayer,
                        toolCallMode = toolCallMode,
                        visionEnabled = model.visionEnabled,
                        recallBlocks = compactedContext.recallBlocks,
                        // 被折叠区域的 provider 可见形态：截断已发生过，与主对话实际发送的字节一致
                        replayPrefix = msgs.take(computedKeepFromIndex),
                    ),
                )
                msgs = compactedContext.messages
                // compact 返回的保留窗口来自原始 transcript（未截断），重放一次截断，
                // 保证与压缩判定时同一口径。
                if (compactionEnabled) {
                    msgs = ContextWindowPolicy.truncateStaleToolResults(msgs, toolCallDetailsOf(msgs))
                }
            }
            if (compactionEnabled) {
                // 巨型用户消息兜底（与 computeKeepFromIndex 同一条折叠线，foldingLimitFor
                // 内部自钳比例）：单条自身超线的用户消息（粘贴长文档/日志）无法按边界折叠，
                // kept 恒超预算 → 每轮请求必被 provider 400。对保留区超大用户消息做
                // 投影级头尾截断，落库 transcript 与 UI 不受影响。
                msgs = ContextWindowPolicy.truncateOversizedUserMessages(
                    msgs,
                    ContextWindowPolicy.foldingLimitFor(
                        budget = budgetTokens,
                        ratioPercent = foldingRatioPercent,
                        systemTokens = ContextWindowPolicy.estimateTokens(systemPrompt) +
                            ContextWindowPolicy.estimateTokens(compactedContext.summaryLayer) +
                            recallTokens,
                        reserveTokens = model.compactionReserveTokens,
                        toolSchemaReserveTokens = toolSchemaReserveTokens,
                    ),
                )
            }
            // 物理字节预检独立于 token 折叠：关闭压缩时历史仍可能把 Nginx/中转撑到 HTTP 413。
            msgs = ImagePayloadCompressor.downscaleHarness(msgs)
            msgs = ContextWindowPolicy.enforceRequestByteBudget(msgs, toolCallDetailsOf(msgs))
            val summaryLayer = compactedContext.summaryLayer
            if (summaryLayer.isNotBlank()) {
                add(
                    ApiMessage(
                        role = "system",
                        content = summaryLayer,
                    ),
                )
            }
            val projectedMessages = ApiMessageProjector.project(
                msgs = msgs,
                toolCallMode = toolCallMode,
                visionEnabled = model.visionEnabled,
                recallSuffixes = compactedContext.recallBlocks,
            )
            // Split-Turn 压缩后保留段可能从 assistant / tool_call 中途开始。补一条合成 user
            // 消息作为角色桥接，满足 provider 的角色交替与「首条消息必须是 user」约束
            // （Anthropic 尤其严格），并提示模型不要重复已折叠的步骤。
            if (summaryLayer.isNotBlank() && projectedMessages.isNotEmpty() && projectedMessages.first().role != "user") {
                add(ApiMessage(role = "user", content = SPLIT_TURN_BRIDGE_MESSAGE))
            }
            addAll(
                ContextWindowPolicy.shrinkApiMessagesToByteBudget(
                    ImagePayloadCompressor.downscale(projectedMessages),
                ),
            )
        }
    }

    private fun toolCallDetailsOf(msgs: List<HarnessMessage>) =
        msgs.filterIsInstance<ToolCall>().associate {
            it.id to ((it.rawToolName ?: HarnessApiMapper.apiName(it.tool)) to it.args)
        }
}

/** Split-Turn 压缩后的角色桥接消息：当保留段从 assistant / tool_call 中途开始时插入。 */
private const val SPLIT_TURN_BRIDGE_MESSAGE =
    "[系统说明] 为避免单个超长任务轮次撑爆上下文，更早的步骤已折叠为上方摘要。以下从当前任务中途继续，请勿重复已完成的工作。"
