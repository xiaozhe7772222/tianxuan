package top.wkbin.tianxuan.di.feature.home

import org.koin.dsl.module
import top.wkbin.tianxuan.ui.home.HomeViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:home module. */
val featureHomeModule = module {
    viewModel<HomeViewModel> {
        HomeViewModel(
            context = get(),
            linuxRuntime = get(),
            environmentDoctor = get(),
            environmentRepairer = get(),
            terminalSessionManager = get(),
            backgroundTaskRegistry = get(),
            privilegeManager = get(),
            logger = get(),
            webChatBridgeServer = get(),
        )
    }
}
