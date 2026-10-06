package top.wkbin.tianxuan.harness


sealed interface TurnProviderOutcome {
    data class Success(val result: ChatResult, val streamText: String) : TurnProviderOutcome
    data class Failed(val message: String) : TurnProviderOutcome
}

sealed interface TurnOutcome {
    data object Complete : TurnOutcome
    data class Continue(
        val effectiveToolCallCount: Int,
        val toolsHadSuccess: Boolean,
        val followUpCount: Int = 0,
    ) : TurnOutcome

    /**
     * 本段工具轮次预算用尽，任务尚未收尾。本轮已完成的工作（工具结果、消费掉的追问）
     * 都已落库，因此这不是失败，而是交由上层决定：续一段新预算，还是就此停下。
     */
    data class RoundLimit(
        val effectiveToolCallCount: Int = 0,
        val toolsHadSuccess: Boolean = true,
    ) : TurnOutcome

    data class Failed(val message: String) : TurnOutcome
}

/**
 * One Agent turn: provider call -> protocol normalization -> durable assistant publication ->
 * follow-up/tool branch. Validation, approval and execution are supplied as one effect port so
 * this state machine stays deterministic while the production port retains its security policy.
 */
class TurnRunner(
    private val normalizer: ProviderResponseNormalizer,
) {
    suspend fun run(
        toolsEnabled: Boolean,
        callProvider: suspend () -> TurnProviderOutcome,
        observeResponse: suspend (NormalizedProviderResponse) -> Unit = {},
        persistAssistant: suspend (NormalizedProviderResponse) -> Unit,
        consumeFollowUps: suspend () -> Int,
        enforceToolLimit: suspend (List<ApiToolCallSpec>, ChatResult) -> List<ApiToolCallSpec>,
        executeTools: suspend (List<ApiToolCallSpec>, ChatResult) -> Boolean,
        remainingRounds: Int = Int.MAX_VALUE,
    ): TurnOutcome {
        if (remainingRounds <= 0) return TurnOutcome.RoundLimit()
        val provider = callProvider()
        if (provider is TurnProviderOutcome.Failed) return TurnOutcome.Failed(provider.message)
        provider as TurnProviderOutcome.Success

        val normalized = normalizer.normalize(provider.result, provider.streamText, toolsEnabled)
        observeResponse(normalized)
        persistAssistant(normalized)

        // 空响应防护：上游 200 但正文/推理/工具调用全空时，绝不能落到下面的
        // "无工具调用 → Complete" 分支——那会把"什么都没说"记成任务完成（Outcome=completed），
        // 前台既不报错也没有内容，即用户反馈的「发消息没有回复」。此处显式失败，让错误可见可重试。
        if (normalized.isBlankResponse) {
            return TurnOutcome.Failed(ProviderClient.EMPTY_RESPONSE_MESSAGE)
        }
        if (normalized.toolCalls.isEmpty() && normalized.hasUnresolvedMarkers) {
            return TurnOutcome.Failed(
                "模型返回了无法解析的文本工具调用；已停止，避免把未执行的工具请求误判为完成",
            )
        }
        if (normalized.toolCalls.isEmpty()) {
            val followUpCount = consumeFollowUps()
            return if (followUpCount == 0) {
                TurnOutcome.Complete
            } else if (remainingRounds == 1) {
                TurnOutcome.RoundLimit()
            } else {
                TurnOutcome.Continue(
                    effectiveToolCallCount = 0,
                    toolsHadSuccess = true,
                    followUpCount = followUpCount,
                )
            }
        }

        val effectiveCalls = enforceToolLimit(normalized.toolCalls, normalized.result)
        val toolsHadSuccess = executeTools(effectiveCalls, normalized.result)
        if (remainingRounds == 1) {
            return TurnOutcome.RoundLimit(
                effectiveToolCallCount = effectiveCalls.size,
                toolsHadSuccess = toolsHadSuccess,
            )
        }
        return TurnOutcome.Continue(
            effectiveToolCallCount = effectiveCalls.size,
            toolsHadSuccess = toolsHadSuccess,
        )
    }
}
