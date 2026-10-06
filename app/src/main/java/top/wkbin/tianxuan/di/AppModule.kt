package top.wkbin.tianxuan.di

import android.content.Context
import androidx.room.Room
import top.wkbin.tianxuan.core.database.ALL_MIGRATIONS
import top.wkbin.tianxuan.core.database.AppDatabase
import top.wkbin.tianxuan.core.database.SCHEMA_VERSION
import top.wkbin.tianxuan.core.database.assertMigrationChainIsContinuous
import top.wkbin.tianxuan.core.database.WorkflowDao
import top.wkbin.tianxuan.core.database.WorkflowScheduleDao
import top.wkbin.tianxuan.core.database.BuildScriptDao
import top.wkbin.tianxuan.core.database.task.AgentTaskDao
import top.wkbin.tianxuan.core.database.ToolDao
import top.wkbin.tianxuan.core.database.InstallLogDao
import top.wkbin.tianxuan.core.database.InstallTaskDao
import top.wkbin.tianxuan.core.database.RuntimeDao
import top.wkbin.tianxuan.core.database.HarnessSessionDao
import top.wkbin.tianxuan.core.database.AiModelDao
import top.wkbin.tianxuan.core.database.WorkspaceDao
import top.wkbin.tianxuan.core.database.TerminalSessionDao
import top.wkbin.tianxuan.core.database.AgentSubagentDao
import top.wkbin.tianxuan.core.database.AgentSkillDao
import top.wkbin.tianxuan.core.database.McpServerDao
import top.wkbin.tianxuan.core.database.McpOAuthCredentialDao
import top.wkbin.tianxuan.core.database.McpOAuthTransactionDao
import top.wkbin.tianxuan.core.database.StorageMountBindingDao
import top.wkbin.tianxuan.core.database.ToolSettingsDao
import top.wkbin.tianxuan.core.database.AgentApprovalDao
import top.wkbin.tianxuan.core.database.QuickPhraseDao
import top.wkbin.tianxuan.core.database.HarnessRuntimeDao
import top.wkbin.tianxuan.core.database.AndroidAppDao
import top.wkbin.tianxuan.harness.WorkspaceFileAccess
import top.wkbin.tianxuan.core.tools.RuntimeManager
import top.wkbin.tianxuan.core.tools.RuntimeManagerImpl
import top.wkbin.tianxuan.core.tools.DependencyManager
import top.wkbin.tianxuan.core.tools.DependencyManagerImpl
import top.wkbin.tianxuan.runtime.shell.ProcessRegistry
import top.wkbin.tianxuan.runtime.shell.ProcessRegistryImpl
import top.wkbin.tianxuan.core.network.HttpClientProvider
import top.wkbin.tianxuan.core.network.FileDownloader
import top.wkbin.tianxuan.core.network.ResumableFileDownloader
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.LinuxRuntimeImpl
import top.wkbin.tianxuan.runtime.shell.ProcessShellExecutor
import top.wkbin.tianxuan.runtime.shell.ShellExecutor
import top.wkbin.tianxuan.runtime.pty.PtyManager
import top.wkbin.tianxuan.runtime.pty.NativePtyManager
import top.wkbin.tianxuan.runtime.service.LocalServiceLauncher
import top.wkbin.tianxuan.runtime.service.LocalServiceLauncherImpl
import top.wkbin.tianxuan.service.AgentForegroundLauncherImpl
import top.wkbin.tianxuan.harness.AgentForegroundLauncher
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient


object AppModule {

    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // 请求/存储时省略 null 字段：未配置的 reasoning_content 不会发给非推理模型
        explicitNulls = false
    }

    fun provideDatabase(context: Context): AppDatabase {
        // 迁移链的连续性在这里做自校验，而不是等Room 在用户手机上抛异常。
        // 断链意味着所有停在该版本的存量用户一升级就崩，且崩溃在启动流程里，
        // 没有兜底也没有降级路径——这是本项目唯一一类必现的启动级故障。
        assertMigrationChainIsContinuous(SCHEMA_VERSION)
        return Room.databaseBuilder(context, AppDatabase::class.java, "tianxuan.db")
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            .build()
    }

    fun provideToolDao(database: AppDatabase): ToolDao = database.toolDao()

    fun provideInstallLogDao(database: AppDatabase): InstallLogDao = database.installLogDao()

    fun provideInstallTaskDao(database: AppDatabase): InstallTaskDao = database.installTaskDao()

    fun provideWorkflowDao(database: AppDatabase): WorkflowDao = database.workflowDao()

    fun provideWorkflowScheduleDao(database: AppDatabase): WorkflowScheduleDao = database.workflowScheduleDao()

    fun provideWorkflowScheduleStore(store: top.wkbin.tianxuan.core.database.RoomWorkflowScheduleStore): top.wkbin.tianxuan.core.database.WorkflowScheduleStore = store

    fun provideRuntimeDao(database: AppDatabase): RuntimeDao = database.runtimeDao()

    fun provideHarnessSessionDao(database: AppDatabase): HarnessSessionDao = database.harnessSessionDao()

    fun provideAiModelDao(database: AppDatabase): AiModelDao = database.aiModelDao()

    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDao = database.workspaceDao()

    fun provideTerminalSessionDao(database: AppDatabase): TerminalSessionDao = database.terminalSessionDao()

    fun provideAgentContextDao(database: AppDatabase): top.wkbin.tianxuan.core.database.AgentContextDao = database.agentContextDao()

    fun provideAgentSubagentDao(database: AppDatabase): AgentSubagentDao = database.agentSubagentDao()

    fun provideMcpServerDao(database: AppDatabase): McpServerDao = database.mcpServerDao()

    fun provideMcpOAuthCredentialDao(database: AppDatabase): McpOAuthCredentialDao = database.mcpOAuthCredentialDao()

    fun provideMcpOAuthTransactionDao(database: AppDatabase): McpOAuthTransactionDao = database.mcpOAuthTransactionDao()

    fun provideAgentSkillDao(database: AppDatabase): AgentSkillDao = database.agentSkillDao()

    fun provideStorageMountBindingDao(database: AppDatabase): StorageMountBindingDao = database.storageMountBindingDao()

    fun provideToolSettingsDao(database: AppDatabase): ToolSettingsDao = database.toolSettingsDao()

    fun provideAgentApprovalDao(database: AppDatabase): AgentApprovalDao = database.agentApprovalDao()

    fun provideQuickPhraseDao(database: AppDatabase): QuickPhraseDao = database.quickPhraseDao()

    fun provideHarnessRuntimeDao(database: AppDatabase): HarnessRuntimeDao = database.harnessRuntimeDao()

    fun provideAndroidAppDao(database: AppDatabase): AndroidAppDao = database.androidAppDao()

    fun provideBuildScriptDao(database: AppDatabase): BuildScriptDao = database.buildScriptDao()

    fun provideAgentTaskDao(database: AppDatabase): AgentTaskDao = database.agentTaskDao()

    fun provideWorkspaceFileAccess(pathManager: top.wkbin.tianxuan.runtime.RuntimePathManager): WorkspaceFileAccess =
        WorkspaceFileAccess(pathManager.workspaceDir)

    /** checkpoint 快照落盘到应用私有目录（linux-runtime/checkpoints/<sessionId>/），模型不可见。 */
    fun provideCheckpointStore(
        pathManager: top.wkbin.tianxuan.runtime.RuntimePathManager,
    ): top.wkbin.tianxuan.harness.checkpoint.CheckpointStore =
        top.wkbin.tianxuan.harness.checkpoint.CheckpointStore().apply {
            persistence = top.wkbin.tianxuan.harness.checkpoint.FileCheckpointPersistence(
                java.io.File(pathManager.baseDir, "checkpoints"),
            )
        }

    fun provideRuntimeManager(impl: RuntimeManagerImpl): RuntimeManager = impl

    fun provideDependencyManager(impl: DependencyManagerImpl): DependencyManager = impl

    fun provideProcessRegistry(impl: ProcessRegistryImpl): ProcessRegistry = impl

    fun provideOkHttpClient(provider: HttpClientProvider): OkHttpClient = provider.create()

    fun provideKtorHttpClient(
        provider: HttpClientProvider,
        okHttpClient: OkHttpClient,
    ): HttpClient = provider.createKtorClient(okHttpClient)

    fun provideFileDownloader(impl: ResumableFileDownloader): FileDownloader = impl

    fun provideShellExecutor(impl: ProcessShellExecutor): ShellExecutor = impl

    fun providePtyManager(impl: NativePtyManager): PtyManager = impl

    fun provideLinuxRuntime(impl: LinuxRuntimeImpl): LinuxRuntime = impl

    fun provideLocalServiceLauncher(impl: LocalServiceLauncherImpl): LocalServiceLauncher = impl

    fun provideAgentForegroundLauncher(impl: AgentForegroundLauncherImpl): AgentForegroundLauncher = impl
}


