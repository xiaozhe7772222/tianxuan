package top.wkbin.tianxuan.di.runtime.browser

import org.koin.dsl.module
import top.wkbin.tianxuan.runtime.browser.BrowserEventBus
import top.wkbin.tianxuan.runtime.browser.BrowserRegistry
import top.wkbin.tianxuan.runtime.browser.di.BrowserModule.provideEventBus
import top.wkbin.tianxuan.runtime.browser.di.BrowserModule.provideRegistry

/** Dependency registrations owned by the runtime:browser module. */
val runtimeBrowserModule = module {
    single<BrowserEventBus> { provideEventBus() }

    single<BrowserRegistry> { provideRegistry(eventBus = get()) }
}
