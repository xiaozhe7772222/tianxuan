package top.wkbin.tianxuan.di.feature.custom.iteration

import org.koin.dsl.module
import top.wkbin.tianxuan.ui.iteration.CustomIterationViewModel
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:custom_iteration module. */
val featureCustomIterationModule = module {
    viewModel<CustomIterationViewModel> { CustomIterationViewModel(application = get()) }
}
