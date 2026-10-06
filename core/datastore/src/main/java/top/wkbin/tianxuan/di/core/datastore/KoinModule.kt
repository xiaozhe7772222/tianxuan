package top.wkbin.tianxuan.di.core.datastore

import org.koin.dsl.module
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.core.datastore.AgentServerPreferences
import top.wkbin.tianxuan.core.datastore.AppStatsPreferences
import top.wkbin.tianxuan.core.datastore.AppearancePreferences
import top.wkbin.tianxuan.core.datastore.BrowserPreferences
import top.wkbin.tianxuan.core.datastore.FirstUseGuidePreferences
import top.wkbin.tianxuan.core.datastore.FtpPreferences
import top.wkbin.tianxuan.core.datastore.OnboardingPreferences
import top.wkbin.tianxuan.core.datastore.ProviderPreferences
import top.wkbin.tianxuan.core.datastore.RegistryPreferences
import top.wkbin.tianxuan.core.datastore.RuntimePreferences
import top.wkbin.tianxuan.core.datastore.SettingsDataStore
import top.wkbin.tianxuan.core.datastore.SshPreferences
import top.wkbin.tianxuan.core.datastore.TerminalPreferences
import top.wkbin.tianxuan.core.datastore.ToolPreferences
import top.wkbin.tianxuan.core.datastore.WorkshopPreferences

/** Dependency registrations owned by the core:datastore module. */
val coreDatastoreModule = module {
    single<AppearancePreferences> { AppearancePreferences(store = get()) }

    single<TerminalPreferences> { TerminalPreferences(store = get()) }

    single<RuntimePreferences> { RuntimePreferences(store = get()) }

    single<WorkshopPreferences> { WorkshopPreferences(store = get()) }

    single<SshPreferences> { SshPreferences(store = get()) }

    single<FtpPreferences> { FtpPreferences(store = get()) }

    single<AgentPreferences> { AgentPreferences(store = get()) }

    single<OnboardingPreferences> { OnboardingPreferences(store = get()) }

    single<ToolPreferences> { ToolPreferences(store = get()) }

    single<FirstUseGuidePreferences> { FirstUseGuidePreferences(store = get()) }

    single<RegistryPreferences> { RegistryPreferences(store = get()) }

    single<AppStatsPreferences> { AppStatsPreferences(store = get()) }

    single<ProviderPreferences> { ProviderPreferences(store = get()) }

    single<BrowserPreferences> { BrowserPreferences(store = get()) }

    single<AgentServerPreferences> { AgentServerPreferences(store = get()) }

    single<SettingsDataStore> { SettingsDataStore(context = get(), secretManager = get()) }
}
