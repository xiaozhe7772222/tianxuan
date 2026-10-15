package top.wkbin.tianxuan.core.model

/**
 * 文件的写效果策略（对齐 DeepSeek Harness sandbox/sandbox/src/index.ts 的 SandboxMode）。
 *
 * - [ReadOnly]：拒绝所有文件写入（模型只能读）
 * - [WorkspaceWrite]：只允许写入工作区根目录内（默认）
 * - [DangerFullAccess]：放行工作区外写入（需显式开启，高风险）
 *
 * 与 [ApprovalMode] 正交：ApprovalMode 决定"是否要审批"，SandboxMode 决定"是否允许写"。
 */
enum class SandboxMode(val id: String, val shortLabel: String) {
    READ_ONLY("read_only", "只读"),
    WORKSPACE_WRITE("workspace_write", "工作区可写"),
    DANGER_FULL_ACCESS("danger_full_access", "全权限");

    companion object {
        fun fromId(id: String?): SandboxMode = entries.firstOrNull { it.id == id } ?: WORKSPACE_WRITE
        /** 严格更宽升级表（对齐 DSH escalation.ts 的 WIDER_MODES）。 */
        fun canEscalateTo(from: SandboxMode, to: SandboxMode): Boolean = when (from) {
            READ_ONLY -> to == WORKSPACE_WRITE || to == DANGER_FULL_ACCESS
            WORKSPACE_WRITE -> to == DANGER_FULL_ACCESS
            DANGER_FULL_ACCESS -> false
        }
    }
}