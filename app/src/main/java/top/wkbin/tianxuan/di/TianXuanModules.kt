package top.wkbin.tianxuan.di

import org.koin.dsl.module
import top.wkbin.tianxuan.di.feature.navigation.navigationModule
import top.wkbin.tianxuan.di.app.appModule
import top.wkbin.tianxuan.di.core.common.coreCommonModule
import top.wkbin.tianxuan.di.core.database.coreDatabaseModule
import top.wkbin.tianxuan.di.core.datastore.coreDatastoreModule
import top.wkbin.tianxuan.di.core.network.coreNetworkModule
import top.wkbin.tianxuan.di.core.security.coreSecurityModule
import top.wkbin.tianxuan.di.feature.onboarding.featureOnboardingModule
import top.wkbin.tianxuan.di.harness.harnessModule
import top.wkbin.tianxuan.di.project.template.projectTemplateModule
import top.wkbin.tianxuan.di.runtime.runtimeModule
import top.wkbin.tianxuan.di.runtime.browser.runtimeBrowserModule
import top.wkbin.tianxuan.di.tools.toolsModule

/** Complete process graph; Android context is supplied at application startup. */
val tianXuanModule = module {
    includes(
        navigationModule,
        appModule,
        coreCommonModule,
        coreDatabaseModule,
        coreDatastoreModule,
        coreNetworkModule,
        coreSecurityModule,
        featureOnboardingModule,
        harnessModule,
        projectTemplateModule,
        runtimeModule,
        runtimeBrowserModule,
        toolsModule,
    )
}
