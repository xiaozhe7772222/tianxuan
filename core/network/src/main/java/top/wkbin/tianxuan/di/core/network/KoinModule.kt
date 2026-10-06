package top.wkbin.tianxuan.di.core.network

import org.koin.dsl.module
import top.wkbin.tianxuan.core.network.AppUpdateManager
import top.wkbin.tianxuan.core.network.CcSwitchClient
import top.wkbin.tianxuan.core.network.ChecksumVerifier
import top.wkbin.tianxuan.core.network.HttpClientProvider
import top.wkbin.tianxuan.core.network.ResumableFileDownloader

/** Dependency registrations owned by the core:network module. */
val coreNetworkModule = module {
    single<AppUpdateManager> { AppUpdateManager(context = get(), httpClient = get()) }

    single<CcSwitchClient> { CcSwitchClient(httpClientProvider = get()) }

    single<ChecksumVerifier> { ChecksumVerifier() }

    single<ResumableFileDownloader> { ResumableFileDownloader(httpClient = get(), checksumVerifier = get()) }

    single<HttpClientProvider> { HttpClientProvider() }
}
