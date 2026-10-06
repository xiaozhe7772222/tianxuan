package top.wkbin.tianxuan.harness.session

import top.wkbin.tianxuan.harness.ApiFunctionCall
import top.wkbin.tianxuan.harness.ApiMessage
import top.wkbin.tianxuan.harness.ApiToolCall
import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.CapabilityEvent
import top.wkbin.tianxuan.harness.HarnessApiMapper
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ModelSwitchEvent
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.harness.SkillSuggestion
import top.wkbin.tianxuan.harness.TextToolCallCodec
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolCallMode
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.ContextWindowPolicy
import top.wkbin.tianxuan.harness.UserMessage

/**
 * Harness 消息 → 提供商协议消息的统一投影。
 *
 * 从 ApiContextAssembler 的转换循环原样抽出，供两处共用同一投影口径：
 * - 主对话组装（ApiContextAssembler）；
 * - 压缩摘要的 cache-replay 请求（CompactionSummarizer）——重放与主请求字节一致
 *   的历史前缀，是命中 provider KV 缓存的前提。
 *
 * [recallSuffixes] 把持久化的用户轮召回后缀（userMessageId → 文本）追加到对应
 * user 消息正文之后；后缀随轮持久化、永不变化，不在此处重新计算。
 */
object ApiMessageProjector {

    fun project(
        msgs: List<HarnessMessage>,
        toolCallMode: ToolCallMode,
        visionEnabled: Boolean,
        recallSuffixes: Map<String, String> = emptyMap(),
    ): List<ApiMessage> {
        val answeredIds = msgs.filterIsInstance<ToolResult>().mapTo(mutableSetOf()) { it.toolCallId }
        val toolCallDetails = msgs.filterIsInstance<ToolCall>().associate {
            it.id to ((it.rawToolName ?: HarnessApiMapper.apiName(it.tool)) to it.args)
        }
        // JSON 文本模式：工具调用以文本表达，tool 消息需转成 user 文本（API 不认识 tool 角色）
        val toolNames = toolCallDetails.mapValuesTo(mutableMapOf()) { it.value.first }

        fun apiToolCall(tc: ToolCall) = ApiToolCall(
            id = tc.id,
            function = ApiFunctionCall(
                name = tc.rawToolName ?: HarnessApiMapper.apiName(tc.tool),
                arguments = tc.args.toString(),
            ),
        )

        fun withRecallSuffix(mapped: ApiMessage, message: HarnessMessage): ApiMessage {
            val suffix = (message as? UserMessage)?.let { recallSuffixes[it.id] }.orEmpty()
            if (suffix.isBlank()) return mapped
            return mapped.copy(content = (mapped.content ?: "") + "\n\n" + suffix)
        }

        return buildList {
            var i = 0
            while (i < msgs.size) {
                val message = msgs[i]
                if (message is CapabilityEvent || message is ModelSwitchEvent || message is SkillSuggestion) {
                    i++
                    continue
                }
                if (toolCallMode == ToolCallMode.JSON_TEXT) {
                    when (message) {
                        is ToolCall -> {
                            toolNames[message.id] = message.rawToolName ?: HarnessApiMapper.apiName(message.tool)
                            // 回放调用意图：落库的 assistant 文本已剥离工具标记，跳过会让模型
                            // 看不到自己上一轮调用了什么参数，结果无法与调用关联，易重复调用。
                            // 与 NATIVE 分支同口径：无结果的悬空调用不回放。
                            if (message.id in answeredIds) {
                                add(
                                    ApiMessage(
                                        role = "assistant",
                                        content = TextToolCallCodec.encodeCall(
                                            message.rawToolName ?: HarnessApiMapper.apiName(message.tool),
                                            message.args.toString(),
                                        ),
                                    ),
                                )
                            }
                            i++
                        }
                        is ToolResult -> {
                            val name = toolNames[message.toolCallId] ?: "工具"
                            val status = if (message.success) "成功" else "失败"
                            val content = "【工具 $name 执行结果·$status】\n${message.output}"
                            add(ApiMessage(role = "user", content = content))
                            visionBridgeMessage(message, visionEnabled)?.let { add(it) }
                            i++
                        }
                        else -> {
                            val mapped = when (message) {
                                is AssistantText ->
                                    HarnessApiMapper.toApiMessage(message).copy(
                                        content = ContextWindowPolicy.assistantTextForContext(
                                            ProviderClient.stripThinkTags(message.text) ?: message.text,
                                        ),
                                        reasoning_content = null,
                                    )
                                else -> HarnessApiMapper.toApiMessage(message)
                            }
                            val withSuffix = withRecallSuffix(mapped, message)
                            add(if (message is UserMessage && !visionEnabled) withSuffix.copy(imageUrls = emptyList()) else withSuffix)
                            i++
                        }
                    }
                    continue
                }
                if (message is AssistantText || message is ToolCall) {
                    if (message is ToolCall && message.id !in answeredIds) {
                        i++
                        continue
                    }
                    val text = (message as? AssistantText)?.text?.let {
                        ContextWindowPolicy.assistantTextForContext(ProviderClient.stripThinkTags(it) ?: it)
                    }
                    val toolCalls = mutableListOf<ApiToolCall>()
                    if (message is ToolCall) toolCalls.add(apiToolCall(message))
                    var j = i + 1
                    while (j < msgs.size && msgs[j] is ToolCall) {
                        val tc = msgs[j] as ToolCall
                        if (tc.id in answeredIds) toolCalls.add(apiToolCall(tc))
                        j++
                    }
                    // DeepSeek 思考模式（V3.2+/V4）：两个 user 消息之间若有工具调用，
                    // 中间 assistant 消息的 reasoning_content 必须原样传回，否则
                    // 400 "The reasoning_content in the thinking mode must be passed back to the API"。
                    // 纯文本 assistant 轮仍不回传（DeepSeek-R1 规则，防推理循环）。
                    val reasoning = if (toolCalls.isNotEmpty()) {
                        (message as? AssistantText)?.reasoning
                            ?: (message as? ToolCall)?.reasoning
                            ?: msgs.subList(i, j).filterIsInstance<ToolCall>().firstNotNullOfOrNull { it.reasoning }
                    } else {
                        null
                    }
                    add(
                        ApiMessage(
                            role = "assistant",
                            content = text,
                            reasoning_content = reasoning,
                            tool_calls = toolCalls.takeIf { it.isNotEmpty() },
                        ),
                    )
                    i = j
                } else if (message is ToolResult) {
                    val content = message.output
                    add(
                        ApiMessage(
                            role = "tool",
                            content = content,
                            tool_call_id = message.toolCallId,
                        ),
                    )
                    // 沙箱图片多模态直通：紧跟 tool 结果追加一条图片 user 消息
                    visionBridgeMessage(message, visionEnabled)?.let { add(it) }
                    i++
                } else {
                    val mapped = HarnessApiMapper.toApiMessage(message)
                    val withSuffix = withRecallSuffix(mapped, message)
                    add(if (message is UserMessage && !visionEnabled) withSuffix.copy(imageUrls = emptyList()) else withSuffix)
                    i++
                }
            }
        }
    }

    /**
     * 沙箱图片多模态直通：把 read 工具留下的 data URL 组装成一条 user 图片消息。
     * 仅在模型开启视觉且工具调用成功时注入；非视觉模型直接忽略，避免请求被拒绝。
     */
    private fun visionBridgeMessage(message: ToolResult, visionEnabled: Boolean): ApiMessage? {
        val payload = message.imageDataUrl ?: return null
        if (!visionEnabled || !message.success) return null
        return ApiMessage(
            role = "user",
            content = "[read 工具读取的图片，已作为多模态图像提供]",
            imageUrls = listOf(payload),
        )
    }
}
