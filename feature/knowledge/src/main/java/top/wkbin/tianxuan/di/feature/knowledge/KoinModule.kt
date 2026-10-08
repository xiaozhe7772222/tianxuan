package top.wkbin.tianxuan.di.feature.knowledge

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import top.wkbin.tianxuan.ui.knowledge.KnowledgeViewModel

val featureKnowledgeModule = module {
    viewModel<KnowledgeViewModel> {
        KnowledgeViewModel(knowledgeManager = get())
    }
}
