package top.wkbin.tianxuan.di.project.template

import org.koin.dsl.module
import top.wkbin.tianxuan.template.ProjectTemplateEngine
import top.wkbin.tianxuan.template.ProjectTemplateStore

/** Dependency registrations owned by the project-template module. */
val projectTemplateModule = module {
    single<ProjectTemplateEngine> { ProjectTemplateEngine(context = get()) }

    single<ProjectTemplateStore> { ProjectTemplateStore(context = get()) }
}
