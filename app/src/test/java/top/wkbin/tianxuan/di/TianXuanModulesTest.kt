package top.wkbin.tianxuan.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import java.io.File
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import okhttp3.OkHttpClient
import top.wkbin.tianxuan.harness.WorkspaceFileAccess
import top.wkbin.tianxuan.harness.subagent.SubagentLaneRunner
import top.wkbin.tianxuan.runtime.browser.tools.BrowserMcpTools
import top.wkbin.tianxuan.runtime.browser.tools.BrowserMcpResources
import top.wkbin.tianxuan.core.common.logging.SensitiveDataRedactor
import top.wkbin.tianxuan.core.security.SecretRedactor
import top.wkbin.tianxuan.harness.projection.CurrentSessionTracker

@OptIn(KoinExperimentalAPI::class)
class TianXuanModulesTest {
    @Test
    fun completeApplicationGraphHasAllConstructorDependencies() {
        tianXuanModule.verify(
            // These arguments are assembled explicitly by provider lambdas, not container lookups.
            injections = injectedParameters(
                definition<WorkspaceFileAccess>(File::class),
                definition<HttpClient>(HttpClientEngine::class),
                definition<OkHttpClient>(OkHttpClient.Builder::class),
                definition<SubagentLaneRunner>(Function0::class),
                definition<BrowserMcpTools>(List::class, Function1::class, top.wkbin.tianxuan.core.browser.BrowserPreferences::class),
                definition<BrowserMcpResources>(Function0::class),
            ),
            extraTypes = listOf(Context::class, Application::class, SavedStateHandle::class, WorkerParameters::class),
        )
    }

    @Test
    fun graphLoadsWithoutOverridesAndPreservesSharedSingletons() {
        val application = koinApplication {
            allowOverride(false)
            modules(tianXuanModule)
        }
        try {
            val koin = application.koin
            assertSame(koin.get<SecretRedactor>(), koin.get<SensitiveDataRedactor>())
            assertSame(koin.get<CurrentSessionTracker>(), koin.get<CurrentSessionTracker>())
        } finally {
            application.close()
        }
    }
}
