package top.wkbin.tianxuan.di.feature.navigation

import org.koin.dsl.module
import top.wkbin.tianxuan.di.feature.browser.featureBrowserModule
import top.wkbin.tianxuan.di.feature.chat.featureChatModule
import top.wkbin.tianxuan.di.feature.custom.iteration.featureCustomIterationModule
import top.wkbin.tianxuan.di.feature.developer.featureDeveloperModule
import top.wkbin.tianxuan.di.feature.git.featureGitModule
import top.wkbin.tianxuan.di.feature.home.featureHomeModule
import top.wkbin.tianxuan.di.feature.knowledge.featureKnowledgeModule
import top.wkbin.tianxuan.di.feature.settings.featureSettingsModule
import top.wkbin.tianxuan.di.feature.terminal.featureTerminalModule
import top.wkbin.tianxuan.di.feature.workflow.featureWorkflowModule
import top.wkbin.tianxuan.di.feature.workspace.featureWorkspaceModule

/** Feature composition stays at the navigation boundary. */
val navigationModule = module {
    includes(
        featureBrowserModule,
        featureChatModule,
        featureCustomIterationModule,
        featureDeveloperModule,
        featureGitModule,
        featureHomeModule,
        featureKnowledgeModule,
        featureSettingsModule,
        featureTerminalModule,
        featureWorkflowModule,
        featureWorkspaceModule,
    )
}
