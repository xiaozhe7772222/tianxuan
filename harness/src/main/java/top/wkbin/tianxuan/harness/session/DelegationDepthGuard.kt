package top.wkbin.tianxuan.harness.session

/**
 * 委派深度递归预算（对齐 DSH SessionHeader.delegationDepth）。
 *
 * 子代理创建时递增 delegationDepth；超过 [MAX_DELEGATION_DEPTH] 时拒绝创建，
 * 防止递归子代理调用无限深递导致资源耗尽。
 *
 * 天玄当前子代理编排器 [SubagentOrchestrator] 在创建子会话时
 * 应调用 [checkDelegationDepth] 强制执行此预算。
 */
object DelegationDepthGuard {

    /** 最大委派深度（对齐 DSH 默认递归预算）。 */
    const val MAX_DELEGATION_DEPTH = 5

    /**
     * 检查父会话的委派深度是否允许创建新的子代理会话。
     *
     * @param parentDepth 父会话的委派深度；null 表示顶层（深度 0）
     * @return 子会话的委派深度；如果超过预算则返回 null
     */
    fun childDepth(parentDepth: Int?): Int? {
        val parent = parentDepth ?: 0
        val child = parent + 1
        return if (child > MAX_DELEGATION_DEPTH) null else child
    }

    /**
     * 检查是否允许创建子代理会话。
     *
     * @param parentDepth 父会话的委派深度
     * @return 允许创建时返回 true
     */
    fun canDelegate(parentDepth: Int?): Boolean {
        val parent = parentDepth ?: 0
        return parent + 1 <= MAX_DELEGATION_DEPTH
    }
}
