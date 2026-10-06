package top.wkbin.tianxuan.runtime.browser.di

import top.wkbin.tianxuan.runtime.browser.BrowserEventBus
import top.wkbin.tianxuan.runtime.browser.BrowserRegistry
import top.wkbin.tianxuan.runtime.browser.BrowserRegistryImpl

object BrowserModule {
    fun provideEventBus(): BrowserEventBus = BrowserEventBus()

    fun provideRegistry(eventBus: BrowserEventBus): BrowserRegistry = BrowserRegistryImpl(eventBus)
}
