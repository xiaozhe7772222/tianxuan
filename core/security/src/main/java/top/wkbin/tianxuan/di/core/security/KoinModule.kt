package top.wkbin.tianxuan.di.core.security

import org.koin.dsl.module
import top.wkbin.tianxuan.core.common.logging.SensitiveDataRedactor
import top.wkbin.tianxuan.core.security.SecretManager
import top.wkbin.tianxuan.core.security.SecretRedactor

/** Dependency registrations owned by the core:security module. */
val coreSecurityModule = module {
    single<SecretManager> { SecretManager() }

    single<SecretRedactor> { SecretRedactor() }

    single<SensitiveDataRedactor> { get<SecretRedactor>() }
}
