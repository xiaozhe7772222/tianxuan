package top.wkbin.tianxuan.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 子代理任务持久化记录（对齐 DeepSeek Harness 的 subagent durable descriptor 设计）。
 *
 * 冷恢复场景：移动端进程被杀是常态。子代理任务在 Room 持久化运行状态，
 * 冷启动时可识别未完成任务并恢复续跑（而非依赖内存 LaneManager 丢失后无法续跑）。
 *
 * 状态：running（进行中）/ completed（已完成）/ failed（失败）/ cancelled（被取消）
 */
@Entity(tableName = "subagent_tasks", indices = [Index("sessionId"), Index("status")])
data class SubagentTaskEntity(
    @PrimaryKey val id: String,
    /** 所属父会话 ID */
    val sessionId: String,
    /** 子代理 lane 名（用于续跑时定位 lane） */
    val laneName: String,
    /** 任务名（展示用） */
    val taskName: String,
    /** 任务状态 */
    val status: String,
    /** 任务描述（续跑用） */
    val description: String = "",
    /** 角色 ID（决定工具/能力配置） */
    val profileId: String = "",
    /** 运行模型 ID */
    val modelId: String? = null,
    /** 模型变体 */
    val modelVariant: String? = null,
    /** 子代理当前进行到的步骤序号 */
    val currentStep: Int = 0,
    /** 错误信息（failed 时） */
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
