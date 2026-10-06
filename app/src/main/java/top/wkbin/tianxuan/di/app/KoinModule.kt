package top.wkbin.tianxuan.di.app

import org.koin.dsl.module
import org.koin.androidx.workmanager.dsl.worker
import top.wkbin.tianxuan.workflow.WorkflowScheduleWorker
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import top.wkbin.tianxuan.core.database.AgentApprovalDao
import top.wkbin.tianxuan.core.database.AgentContextDao
import top.wkbin.tianxuan.core.database.AgentSkillDao
import top.wkbin.tianxuan.core.database.AgentSubagentDao
import top.wkbin.tianxuan.core.database.AiModelDao
import top.wkbin.tianxuan.core.database.AndroidAppDao
import top.wkbin.tianxuan.core.database.AppDatabase
import top.wkbin.tianxuan.core.database.BuildScriptDao
import top.wkbin.tianxuan.core.database.HarnessRuntimeDao
import top.wkbin.tianxuan.core.database.HarnessSessionDao
import top.wkbin.tianxuan.core.database.InstallLogDao
import top.wkbin.tianxuan.core.database.InstallTaskDao
import top.wkbin.tianxuan.core.database.McpOAuthCredentialDao
import top.wkbin.tianxuan.core.database.McpOAuthTransactionDao
import top.wkbin.tianxuan.core.database.McpServerDao
import top.wkbin.tianxuan.core.database.QuickPhraseDao
import top.wkbin.tianxuan.core.database.RuntimeDao
import top.wkbin.tianxuan.core.database.StorageMountBindingDao
import top.wkbin.tianxuan.core.database.TerminalSessionDao
import top.wkbin.tianxuan.core.database.ToolDao
import top.wkbin.tianxuan.core.database.ToolSettingsDao
import top.wkbin.tianxuan.core.database.WorkflowDao
import top.wkbin.tianxuan.core.database.WorkflowScheduleDao
import top.wkbin.tianxuan.core.database.WorkflowScheduleStore
import top.wkbin.tianxuan.core.database.WorkspaceDao
import top.wkbin.tianxuan.core.database.task.AgentTaskDao
import top.wkbin.tianxuan.core.network.FileDownloader
import top.wkbin.tianxuan.core.tools.DependencyManager
import top.wkbin.tianxuan.core.tools.RuntimeManager
import top.wkbin.tianxuan.core.tools.ToolRuntimeAdapter
import top.wkbin.tianxuan.di.AppModule.provideAgentApprovalDao
import top.wkbin.tianxuan.di.AppModule.provideAgentContextDao
import top.wkbin.tianxuan.di.AppModule.provideAgentForegroundLauncher
import top.wkbin.tianxuan.di.AppModule.provideAgentSkillDao
import top.wkbin.tianxuan.di.AppModule.provideAgentSubagentDao
import top.wkbin.tianxuan.di.AppModule.provideAgentTaskDao
import top.wkbin.tianxuan.di.AppModule.provideAiModelDao
import top.wkbin.tianxuan.di.AppModule.provideAndroidAppDao
import top.wkbin.tianxuan.di.AppModule.provideBuildScriptDao
import top.wkbin.tianxuan.di.AppModule.provideCheckpointStore
import top.wkbin.tianxuan.di.AppModule.provideDatabase
import top.wkbin.tianxuan.di.AppModule.provideDependencyManager
import top.wkbin.tianxuan.di.AppModule.provideFileDownloader
import top.wkbin.tianxuan.di.AppModule.provideHarnessRuntimeDao
import top.wkbin.tianxuan.di.AppModule.provideHarnessSessionDao
import top.wkbin.tianxuan.di.AppModule.provideInstallLogDao
import top.wkbin.tianxuan.di.AppModule.provideInstallTaskDao
import top.wkbin.tianxuan.di.AppModule.provideJson
import top.wkbin.tianxuan.di.AppModule.provideKtorHttpClient
import top.wkbin.tianxuan.di.AppModule.provideLinuxRuntime
import top.wkbin.tianxuan.di.AppModule.provideLocalServiceLauncher
import top.wkbin.tianxuan.di.AppModule.provideMcpOAuthCredentialDao
import top.wkbin.tianxuan.di.AppModule.provideMcpOAuthTransactionDao
import top.wkbin.tianxuan.di.AppModule.provideMcpServerDao
import top.wkbin.tianxuan.di.AppModule.provideOkHttpClient
import top.wkbin.tianxuan.di.AppModule.provideProcessRegistry
import top.wkbin.tianxuan.di.AppModule.providePtyManager
import top.wkbin.tianxuan.di.AppModule.provideQuickPhraseDao
import top.wkbin.tianxuan.di.AppModule.provideRuntimeDao
import top.wkbin.tianxuan.di.AppModule.provideRuntimeManager
import top.wkbin.tianxuan.di.AppModule.provideShellExecutor
import top.wkbin.tianxuan.di.AppModule.provideStorageMountBindingDao
import top.wkbin.tianxuan.di.AppModule.provideTerminalSessionDao
import top.wkbin.tianxuan.di.AppModule.provideToolDao
import top.wkbin.tianxuan.di.AppModule.provideToolSettingsDao
import top.wkbin.tianxuan.di.AppModule.provideWorkflowDao
import top.wkbin.tianxuan.di.AppModule.provideWorkflowScheduleDao
import top.wkbin.tianxuan.di.AppModule.provideWorkflowScheduleStore
import top.wkbin.tianxuan.di.AppModule.provideWorkspaceDao
import top.wkbin.tianxuan.di.AppModule.provideWorkspaceFileAccess
import top.wkbin.tianxuan.harness.AgentForegroundLauncher
import top.wkbin.tianxuan.harness.WorkspaceFileAccess
import top.wkbin.tianxuan.harness.agent.AgentMcpForegroundLauncher
import top.wkbin.tianxuan.harness.checkpoint.CheckpointStore
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduleDispatcher
import top.wkbin.tianxuan.lifecycle.RuntimeLifecycleSupervisor
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.pty.PtyManager
import top.wkbin.tianxuan.runtime.service.LocalServiceLauncher
import top.wkbin.tianxuan.runtime.service.RuntimeServiceController
import top.wkbin.tianxuan.runtime.shell.ProcessRegistry
import top.wkbin.tianxuan.runtime.shell.ShellExecutor
import top.wkbin.tianxuan.runtime.tools.CodexToolInstaller
import top.wkbin.tianxuan.runtime.tools.HelloToolInstaller
import top.wkbin.tianxuan.runtime.webchat.WebChatAgentGateway
import top.wkbin.tianxuan.service.AgentForegroundLauncherImpl
import top.wkbin.tianxuan.service.AgentMcpForegroundLauncherImpl
import top.wkbin.tianxuan.service.adb.AdbNotificationManager
import top.wkbin.tianxuan.webchat.TianXuanWebChatAgentGateway
import top.wkbin.tianxuan.workflow.AppForegroundTracker
import top.wkbin.tianxuan.workflow.WorkManagerScheduleDispatcher
import top.wkbin.tianxuan.workflow.WorkManagerScheduleDispatcher.ScheduleDispatcherModule.provideDispatcher
import top.wkbin.tianxuan.workflow.WorkflowApprovalNotifier
import top.wkbin.tianxuan.workflow.WorkflowRunUiController
import org.koin.core.qualifier.named

/** Dependency registrations owned by the app module. */
val appModule = module {
    worker { params ->
        WorkflowScheduleWorker(get(), params.get(), get(), get())
    }

    single<Json> { provideJson() }

    single<AppDatabase> { provideDatabase(context = get()) }

    single<ToolDao> { provideToolDao(database = get()) }

    single<InstallLogDao> { provideInstallLogDao(database = get()) }

    single<InstallTaskDao> { provideInstallTaskDao(database = get()) }

    single<WorkflowDao> { provideWorkflowDao(database = get()) }

    single<WorkflowScheduleDao> { provideWorkflowScheduleDao(database = get()) }

    single<WorkflowScheduleStore> { provideWorkflowScheduleStore(store = get()) }

    single<RuntimeDao> { provideRuntimeDao(database = get()) }

    single<HarnessSessionDao> { provideHarnessSessionDao(database = get()) }

    single<AiModelDao> { provideAiModelDao(database = get()) }

    single<WorkspaceDao> { provideWorkspaceDao(database = get()) }

    single<TerminalSessionDao> { provideTerminalSessionDao(database = get()) }

    single<AgentContextDao> { provideAgentContextDao(database = get()) }

    single<AgentSubagentDao> { provideAgentSubagentDao(database = get()) }

    single<McpServerDao> { provideMcpServerDao(database = get()) }

    single<McpOAuthCredentialDao> { provideMcpOAuthCredentialDao(database = get()) }

    single<McpOAuthTransactionDao> { provideMcpOAuthTransactionDao(database = get()) }

    single<AgentSkillDao> { provideAgentSkillDao(database = get()) }

    single<StorageMountBindingDao> { provideStorageMountBindingDao(database = get()) }

    single<ToolSettingsDao> { provideToolSettingsDao(database = get()) }

    single<AgentApprovalDao> { provideAgentApprovalDao(database = get()) }

    single<QuickPhraseDao> { provideQuickPhraseDao(database = get()) }

    single<HarnessRuntimeDao> { provideHarnessRuntimeDao(database = get()) }

    single<AndroidAppDao> { provideAndroidAppDao(database = get()) }

    single<BuildScriptDao> { provideBuildScriptDao(database = get()) }

    single<AgentTaskDao> { provideAgentTaskDao(database = get()) }

    single<WorkspaceFileAccess> { provideWorkspaceFileAccess(pathManager = get()) }

    single<CheckpointStore> { provideCheckpointStore(pathManager = get()) }

    single<RuntimeManager> { provideRuntimeManager(impl = get()) }

    single<DependencyManager> { provideDependencyManager(impl = get()) }

    single<ProcessRegistry> { provideProcessRegistry(impl = get()) }

    single<OkHttpClient> { provideOkHttpClient(provider = get()) }

    single<HttpClient> { provideKtorHttpClient(provider = get(), okHttpClient = get()) }

    single<FileDownloader> { provideFileDownloader(impl = get()) }

    single<ShellExecutor> { provideShellExecutor(impl = get()) }

    single<PtyManager> { providePtyManager(impl = get()) }

    single<LinuxRuntime> { provideLinuxRuntime(impl = get()) }

    single<LocalServiceLauncher> { provideLocalServiceLauncher(impl = get()) }

    single<AgentForegroundLauncher> { provideAgentForegroundLauncher(impl = get()) }

    single<AgentMcpForegroundLauncher> { AgentMcpForegroundLauncherImpl(context = get()) }

    single<WorkflowScheduleDispatcher> { provideDispatcher(context = get()) }

    factory<WebChatAgentGateway> { get<TianXuanWebChatAgentGateway>() }

    single<RuntimeServiceController> { RuntimeServiceController(context = get()) }

    single<RuntimeLifecycleSupervisor> { RuntimeLifecycleSupervisor(context = get()) }

    factory<AgentForegroundLauncherImpl> { AgentForegroundLauncherImpl(context = get()) }

    single<AdbNotificationManager> {
        AdbNotificationManager(
            context = get(),
            embeddedAdbManager = get(),
            preferences = get(),
        )
    }

    single<TianXuanWebChatAgentGateway> {
        TianXuanWebChatAgentGateway(
            harnessLoop = get(),
            sessions = get(),
            models = get(),
            approvals = get(),
        )
    }

    single<AppForegroundTracker> { AppForegroundTracker() }

    single<WorkflowApprovalNotifier> { WorkflowApprovalNotifier(runManager = get(), foregroundTracker = get()) }

    single<WorkflowRunUiController> {
        WorkflowRunUiController(
            runManager = get(),
            hud = get(),
            appContext = get(),
        )
    }

    factory<WorkManagerScheduleDispatcher> { WorkManagerScheduleDispatcher(context = get()) }

    single<Set<ToolRuntimeAdapter>>(named("toolAdapters")) {
        setOf(get<HelloToolInstaller>(), get<CodexToolInstaller>())
    }
}
