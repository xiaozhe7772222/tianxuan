package top.wkbin.tianxuan.di.feature.chat

import org.koin.dsl.module
import top.wkbin.tianxuan.ui.chat.ChatViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:chat module. */
val featureChatModule = module {
    viewModel<ChatViewModel> {
        ChatViewModel(
            context = get(),
            savedStateHandle = get(),
            harnessLoop = get(),
            systemPromptBuilder = get(),
            sessionDao = get(),
            aiModelDao = get(),
            workspaceManager = get(),
            settingsDataStore = get(),
            linuxRuntime = get(),
            terminalSessionManager = get(),
            mcpManager = get(),
            agentSkillRepository = get(),
            mcpServerRepository = get(),
            approvalRepository = get(),
            agentContextDao = get(),
            compactionManager = get(),
            sessionModelSwitcher = get(),
            quickPhraseRepository = get(),
            laneManager = get(),
            eventBus = get(),
            proactiveWorkflowAdvisor = get(),
            modelDiscovery = get(),
            providerCatalog = get(),
            providerRepository = get(),
            profileWriter = get(),
            privilegeManager = get(),
            pathManager = get(),
            workflowRepository = get(),
            translationManager = getOrNull(),
            globalNavigationBus = getOrNull(),
        )
    }
}
