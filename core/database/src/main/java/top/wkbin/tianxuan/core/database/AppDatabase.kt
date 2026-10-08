package top.wkbin.tianxuan.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import top.wkbin.tianxuan.core.database.task.AgentTaskEntity
import top.wkbin.tianxuan.core.database.task.AgentTaskDao

/**
 * 当前 schema 版本。迁移链的终点，升版本时必须同步补迁移（见 MigrationRegistry）。
 *
 * 提成常量而不是散落的字面量：过去 `@Database(version = 53)` 与迁移链终点各写一处，
 * 改版本时漏改任一处，Room 只会在用户设备上抛 IllegalStateException 才暴露。
 */
const val SCHEMA_VERSION: Int = 54

@Database(
    entities = [
        ToolEntity::class,
        InstallLogEntity::class,
        InstallTaskEntity::class,
        RuntimeEntity::class,
        RuntimeDependencyRefEntity::class,
        HarnessSessionEntity::class,
        AiModelEntity::class,
        WorkspaceEntity::class,
        TerminalSessionEntity::class,
        AgentMemoryEntity::class,
        AgentPlanEntity::class,
        AgentScratchpadEntity::class,
        AgentSubagentEntity::class,
        AgentSubagentSettingsEntity::class,
        McpServerEntity::class,
        McpOAuthCredentialEntity::class,
        McpOAuthTransactionEntity::class,
        AgentSkillEntity::class,
        StorageMountBindingEntity::class,
        ToolSettingsEntity::class,
        AgentApprovalRequestEntity::class,
        AgentApprovalSettingsEntity::class,
        QuickPhraseEntity::class,
        HarnessEntryEntity::class,
        HarnessLaneEntity::class,
        HarnessOperationEntity::class,
        HarnessQueueItemEntity::class,
        HarnessUsageEntity::class,
        HarnessLaneResultEntity::class,
        AndroidAppEntity::class,
        BuildScriptEntity::class,
        ProjectBuildScriptBindingEntity::class,
        AgentTaskEntity::class,
        WorkflowEntity::class,
        WorkflowExecutionLogEntity::class,
        WorkflowScheduleEntity::class,
        KbDocumentEntity::class,
        KbChunkEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun installLogDao(): InstallLogDao
    abstract fun installTaskDao(): InstallTaskDao
    abstract fun runtimeDao(): RuntimeDao
    abstract fun harnessSessionDao(): HarnessSessionDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun terminalSessionDao(): TerminalSessionDao
    abstract fun agentContextDao(): AgentContextDao
    abstract fun agentSubagentDao(): AgentSubagentDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun mcpOAuthCredentialDao(): McpOAuthCredentialDao
    abstract fun mcpOAuthTransactionDao(): McpOAuthTransactionDao
    abstract fun agentSkillDao(): AgentSkillDao
    abstract fun storageMountBindingDao(): StorageMountBindingDao
    abstract fun toolSettingsDao(): ToolSettingsDao
    abstract fun agentApprovalDao(): AgentApprovalDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    abstract fun harnessRuntimeDao(): HarnessRuntimeDao
    abstract fun androidAppDao(): AndroidAppDao
    abstract fun buildScriptDao(): BuildScriptDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun workflowDao(): WorkflowDao
    abstract fun workflowScheduleDao(): WorkflowScheduleDao
    abstract fun knowledgeDao(): KnowledgeDao
}

