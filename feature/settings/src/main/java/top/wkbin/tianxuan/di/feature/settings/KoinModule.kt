package top.wkbin.tianxuan.di.feature.settings

import org.koin.dsl.module
import top.wkbin.tianxuan.ui.settings.AppManagementViewModel
import top.wkbin.tianxuan.ui.settings.CcSwitchViewModel
import top.wkbin.tianxuan.ui.settings.FtpSettingsViewModel
import top.wkbin.tianxuan.ui.settings.LocalLlmViewModel
import top.wkbin.tianxuan.ui.settings.SettingsViewModel
import top.wkbin.tianxuan.ui.settings.SponsorListRepository
import top.wkbin.tianxuan.ui.settings.SponsorListViewModel
import top.wkbin.tianxuan.ui.settings.SshSettingsViewModel
import top.wkbin.tianxuan.ui.settings.StorageUsageViewModel
import top.wkbin.tianxuan.ui.settings.ToolCenterViewModel
import top.wkbin.tianxuan.ui.settings.ToolDetailViewModel
import top.wkbin.tianxuan.ui.settings.stats.StatsRepository
import top.wkbin.tianxuan.ui.settings.stats.StatsViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:settings module. */
val featureSettingsModule = module {
    viewModel<AppManagementViewModel> { AppManagementViewModel(repository = get(), appManager = get()) }

    viewModel<CcSwitchViewModel> {
        CcSwitchViewModel(
            toolManager = get(),
            ccSwitchClient = get(),
            aiModelDao = get(),
            providerRepository = get(),
            toolSettingsRepository = get(),
            linuxRuntime = get(),
            dependencyManager = get(),
            logger = get(),
        )
    }

    viewModel<FtpSettingsViewModel> {
        FtpSettingsViewModel(
            context = get(),
            linuxRuntime = get(),
            preferences = get(),
            manager = get(),
        )
    }

    viewModel<LocalLlmViewModel> {
        LocalLlmViewModel(
            context = get(),
            localLlmManager = get(),
            toolManager = get(),
            linuxRuntime = get(),
            aiModelRepository = get(),
        )
    }

    viewModel<SettingsViewModel> {
        SettingsViewModel(
            application = get(),
            logger = get(),
            appearancePreferences = get(),
            terminalPreferences = get(),
            runtimePreferences = get(),
            agentPreferences = get(),
            firstUseGuidePreferences = get(),
            providerRepository = get(),
            aiModelDao = get(),
            modelDiscovery = get(),
            providerCatalogRepository = get(),
            connectionTester = get(),
            privilegeManager = get(),
            mcpManager = get(),
            linuxRuntime = get(),
            pathManager = get(),
            linuxEnvironmentManager = get(),
            appUpdateManager = get(),
            subagentRepository = get(),
            agentSkillRepository = get(),
            mcpServerRepository = get(),
            mcpOAuthCoordinator = get(),
            mcpOAuthCredentials = get(),
            storageMountBindingRepository = get(),
            approvalRepository = get(),
            sessionDao = get(),
            toolManager = get(),
            quickPhraseRepository = get(),
            profileWriter = get(),
            profileBackupCodec = get(),
            webChatBridgeServer = get(),
            browserPrefs = get(),
            agentServerPreferences = get(),
            translationManager = get(),
            skillInstallationManager = getOrNull(),
            clawHubClient = getOrNull(),
        )
    }

    single<SponsorListRepository> { SponsorListRepository(httpClient = get()) }

    viewModel<SponsorListViewModel> { SponsorListViewModel(repository = get()) }

    viewModel<SshSettingsViewModel> {
        SshSettingsViewModel(
            context = get(),
            linuxRuntime = get(),
            preferences = get(),
            manager = get(),
        )
    }

    viewModel<StorageUsageViewModel> { StorageUsageViewModel(storageManager = get()) }

    viewModel<ToolCenterViewModel> {
        ToolCenterViewModel(
            toolManager = get(),
            linuxRuntime = get(),
            firstUseGuidePreferences = get(),
            logger = get(),
        )
    }

    viewModel<ToolDetailViewModel> {
        ToolDetailViewModel(
            toolManager = get(),
            settingsDataStore = get(),
            providerRepository = get(),
            aiModelDao = get(),
            toolSettingsRepository = get(),
            linuxRuntime = get(),
            logger = get(),
        )
    }

    single<StatsRepository> {
        StatsRepository(
            runtimeRepository = get(),
            sessionRepository = get(),
            aiModelRepository = get(),
            appStatsPreferences = get(),
        )
    }

    viewModel<StatsViewModel> { StatsViewModel(statsRepository = get()) }
}
