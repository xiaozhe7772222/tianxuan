package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.model.ToolManifest
import top.wkbin.tianxuan.core.model.RuntimeName
import org.junit.Assert.assertEquals
import org.junit.Test

class DependencyResolverTest {
    @Test
    fun resolvesOnlyKnownRuntimeDependenciesInManifestOrder() {
        val requirements = DependencyResolver().resolve(
            ToolManifest(
                id = "demo-tool",
                name = "Demo",
                description = "测试依赖解析",
                dependencies = listOf("node>=22.22.3", "git", "unsupported"),
            ),
        )

        assertEquals(
            listOf(RuntimeName.NODE, RuntimeName.GIT),
            requirements.map { it.name },
        )
        assertEquals(">=22.22.3", requirements.first().constraint)
    }
}
