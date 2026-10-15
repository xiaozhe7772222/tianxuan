package top.wkbin.tianxuan.harness.session

import kotlinx.serialization.Serializable

/**
 * 消息如何进入模型可见 surface（对齐 DSH session/types.ts 的 SurfaceOp）。
 *
 * 会话事件日志的 source of truth 是 append-only 的事件序列；模型可见的历史
 * 由 surface 折叠（fold）派生。压缩、分叉等操作以 `Replace` 原子地替换一段
 * 既有 surface 节点，而不是另开一条叙事——替换后的摘要节点持有被遮蔽区间的
 * 引用，追溯（lineage）仍可达。
 *
 * - [Append]：追加到尾部——用户/助手/工具消息的正常路径。
 * - [Replace]：把从 startSeq（含）到 endSeq（含）的 surface 节点替换为
 *   当前节点（由压缩摘要承担）。startSeq == endSeq 时替换单个节点。
 */
@Serializable
sealed interface SurfaceOp {
    /** 追加到 surface 尾部。 */
    @Serializable
    data object Append : SurfaceOp

    /**
     * 替换 surface 上一段既有节点。
     *
     * @property startSeq 被替换区间起始事件序号（含）。
     * @property endSeq 被替换区间结束事件序号（含）。
     */
    @Serializable
    data class Replace(
        val startSeq: Long,
        val endSeq: Long,
    ) : SurfaceOp
}
