package top.wkbin.tianxuan.di.core.database

import org.koin.dsl.module
import top.wkbin.tianxuan.core.database.AgencyAgentCatalogLoader
import top.wkbin.tianxuan.core.database.AgentApprovalRepository
import top.wkbin.tianxuan.core.database.AgentContextRepository
import top.wkbin.tianxuan.core.database.AgentSkillRepository
import top.wkbin.tianxuan.core.database.AgentSubagentRepository
import top.wkbin.tianxuan.core.database.AiModelRepository
import top.wkbin.tianxuan.core.database.AndroidAppRepository
import top.wkbin.tianxuan.core.database.BuildScriptRepository
import top.wkbin.tianxuan.core.database.HarnessBlobStore
import top.wkbin.tianxuan.core.database.HarnessRuntimeRepository
import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.core.database.McpOAuthCredentialRepository
import top.wkbin.tianxuan.core.database.McpServerRepository
import top.wkbin.tianxuan.core.database.QuickPhraseRepository
import top.wkbin.tianxuan.core.database.RoomAgentContextRepository
import top.wkbin.tianxuan.core.database.RoomAiModelRepository
import top.wkbin.tianxuan.core.database.RoomAndroidAppRepository
import top.wkbin.tianxuan.core.database.RoomBuildScriptRepository
import top.wkbin.tianxuan.core.database.RoomHarnessRuntimeRepository
import top.wkbin.tianxuan.core.database.RoomHarnessSessionRepository
import top.wkbin.tianxuan.core.database.RoomQuickPhraseRepository
import top.wkbin.tianxuan.core.database.RoomTerminalSessionRepository
import top.wkbin.tianxuan.core.database.RoomWorkflowRepository
import top.wkbin.tianxuan.core.database.RoomWorkflowScheduleStore
import top.wkbin.tianxuan.core.database.RoomWorkspaceRepository
import top.wkbin.tianxuan.core.database.StorageMountBindingRepository
import top.wkbin.tianxuan.core.database.TerminalSessionRepository
import top.wkbin.tianxuan.core.database.ToolSettingsRepository
import top.wkbin.tianxuan.core.database.WorkflowRepository
import top.wkbin.tianxuan.core.database.WorkspaceRepository
import top.wkbin.tianxuan.core.database.task.AgentTaskRepository
import top.wkbin.tianxuan.core.database.task.RoomAgentTaskRepository

/** Dependency registrations owned by the core:database module. */
val coreDatabaseModule = module {
    single<AgencyAgentCatalogLoader> { AgencyAgentCatalogLoader(context = get(), json = get()) }

    single<AgentApprovalRepository> { AgentApprovalRepository(dao = get()) }

    single<AgentSkillRepository> { AgentSkillRepository(dao = get()) }

    single<AgentSubagentRepository> { AgentSubagentRepository(dao = get(), catalogLoader = get()) }

    single<HarnessBlobStore> { HarnessBlobStore(context = get()) }

    single<RoomHarnessRuntimeRepository> { RoomHarnessRuntimeRepository(dao = get(), blobStore = get()) }

    single<McpOAuthCredentialRepository> {
        McpOAuthCredentialRepository(
            credentials = get(),
            transactions = get(),
            secretManager = get(),
        )
    }

    single<McpServerRepository> { McpServerRepository(dao = get(), secretManager = get()) }

    single<RoomBuildScriptRepository> { RoomBuildScriptRepository(dao = get()) }

    single<RoomAndroidAppRepository> { RoomAndroidAppRepository(dao = get()) }

    single<RoomAiModelRepository> { RoomAiModelRepository(dao = get()) }

    single<RoomHarnessSessionRepository> { RoomHarnessSessionRepository(dao = get()) }

    single<RoomWorkspaceRepository> { RoomWorkspaceRepository(dao = get()) }

    single<RoomTerminalSessionRepository> { RoomTerminalSessionRepository(dao = get()) }

    single<RoomAgentContextRepository> { RoomAgentContextRepository(dao = get()) }

    single<RoomQuickPhraseRepository> { RoomQuickPhraseRepository(dao = get()) }

    factory<AiModelRepository> { get<RoomAiModelRepository>() }

    factory<HarnessSessionRepository> { get<RoomHarnessSessionRepository>() }

    factory<WorkspaceRepository> { get<RoomWorkspaceRepository>() }

    factory<TerminalSessionRepository> { get<RoomTerminalSessionRepository>() }

    factory<AgentContextRepository> { get<RoomAgentContextRepository>() }

    factory<AndroidAppRepository> { get<RoomAndroidAppRepository>() }

    factory<QuickPhraseRepository> { get<RoomQuickPhraseRepository>() }

    factory<HarnessRuntimeRepository> { get<RoomHarnessRuntimeRepository>() }

    factory<BuildScriptRepository> { get<RoomBuildScriptRepository>() }

    single<StorageMountBindingRepository> { StorageMountBindingRepository(dao = get()) }

    single<ToolSettingsRepository> { ToolSettingsRepository(dao = get()) }

    single<RoomWorkflowScheduleStore> { RoomWorkflowScheduleStore(dao = get()) }

    single<RoomWorkflowRepository> { RoomWorkflowRepository(dao = get(), json = get()) }

    factory<WorkflowRepository> { get<RoomWorkflowRepository>() }

    single<RoomAgentTaskRepository> { RoomAgentTaskRepository(dao = get()) }

    factory<AgentTaskRepository> { get<RoomAgentTaskRepository>() }
}
