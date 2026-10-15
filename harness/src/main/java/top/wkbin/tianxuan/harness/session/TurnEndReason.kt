package top.wkbin.tianxuan.harness.session

import kotlinx.serialization.Serializable

/**
 * 会话事件日志中的「轮次结束原因」（对齐 DSH session/types.ts 的 TurnEndReasonMap）。
 *
 * DSH 把每轮结束归因持久化进会话日志，使恢复、压缩与回溯可精确区分
 * 完成/中断/错误/token 上限/分叉等场景，而不是笼统地看到「最后一条 assistant 消息」。
 *
 * 天玄当前 TurnOutcome 是运行时态（不落库），TurnEndReason 是持久化归因；
 * 两者一一对应，TurnRunner 结束后由调用方写入会话日志。
 */
@Serializable
sealed interface TurnEndReason {

    val kind: String

    /** 正常完成：无更多工具调用，无追问，assistant 消息为最终回复。 */
    @Serializable
    data object Completed : TurnEndReason {
        override val kind: String get() = "completed"
    }

    /** 被用户取消（用户点击停止 / 会话被销毁）。 */
    @Serializable
    data class Aborted(val cause: AbortCause) : TurnEndReason {
        override val kind: String get() = "aborted"
    }

    /** 工具被审批策略阻止，无可见输出。 */
    @Serializable
    data object Blocked : TurnEndReason {
        override val kind: String get() = "blocked"
    }

    /** LLM 或工具执行出错。 */
    @Serializable
    data class Error(val errorCode: String, val errorMessage: String) : TurnEndReason {
        override val kind: String get() = "error"
    }

    /** 至少一步触达输出 token 上限，即便插件继续了轮次。 */
    @Serializable
    data object MaxTokens : TurnEndReason {
        override val kind: String get() = "max-tokens"
    }

    /** 进程崩溃后恢复：会话日志中最后一轮没有正常收尾。 */
    @Serializable
    data object Interrupted : TurnEndReason {
        override val kind: String get() = "interrupted"
    }

    /** 分叉边界：从源会话分叉出的新会话，其前轮在源会话中未结束。 */
    @Serializable
    data object Forked : TurnEndReason {
        override val kind: String get() = "forked"
    }

    /** 取消原因（对齐 DSH AgentCancelCause）。 */
    @Serializable
    sealed interface AbortCause {
        val kind: String

        /** 用户主动点击停止。 */
        @Serializable
        data object User : AbortCause {
            override val kind: String get() = "user"
        }

        /** 父会话终止（子代理场景）。 */
        @Serializable
        data object Parent : AbortCause {
            override val kind: String get() = "parent"
        }

        /** Hook 策略拒绝。 */
        @Serializable
        data class Hook(val reason: String) : AbortCause {
            override val kind: String get() = "hook"
        }

        /** 宿主进程销毁。 */
        @Serializable
        data object Disposed : AbortCause {
            override val kind: String get() = "disposed"
        }
    }
}
