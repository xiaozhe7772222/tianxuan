package top.wkbin.tianxuan.di.core.common

import org.koin.dsl.module
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.common.logging.CrashReporter
import top.wkbin.tianxuan.core.common.navigation.GlobalNavigationBus

/** Dependency registrations owned by the core:common module. */
val coreCommonModule = module {
    single<AppLogger> { AppLogger(context = get(), secretRedactor = get()) }

    single<CrashReporter> { CrashReporter(context = get(), secretRedactor = get()) }

    single<GlobalNavigationBus> { GlobalNavigationBus() }

    single<top.wkbin.tianxuan.core.common.translation.TranslationManager> {
        top.wkbin.tianxuan.core.common.translation.TranslationManager(context = get(), appLogger = get())
    }
}
