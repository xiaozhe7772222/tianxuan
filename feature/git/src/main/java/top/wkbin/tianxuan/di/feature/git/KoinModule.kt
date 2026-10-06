package top.wkbin.tianxuan.di.feature.git

import org.koin.dsl.module
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.ui.git.GitCredentialsStore
import top.wkbin.tianxuan.ui.git.GitManager
import top.wkbin.tianxuan.ui.git.GitViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:git module. */
val featureGitModule = module {
    single<GitCredentialsStore> { GitCredentialsStore(context = get(), secretManager = get()) }

    single<GitManager> { GitManager(credentialsStore = get(), linuxRuntime = lazy { get<LinuxRuntime>() }) }

    viewModel<GitViewModel> {
        GitViewModel(
            workspaceManager = get(),
            gitManager = get(),
            credentialsStore = get(),
            providerClient = get(),
        )
    }
}
