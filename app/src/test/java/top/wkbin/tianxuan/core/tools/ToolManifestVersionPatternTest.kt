package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.model.ToolManifest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `manifest.version` 会直接参与本地插件落盘路径 `plugins/<id>/<version>/`，
 * 因此必须是白名单字符集。此前只做 `isNotBlank()`，导致形如
 * `"../../../shared_prefs/x"` 的版本号可以让 `File(localRoot, "$id/$version")`
 * 归一化后逃出 `files/plugins`（同版本覆盖分支会先 renameTo 搬走目标、
 * 写入后再 deleteRecursively，等于任意覆盖写原语）。
 */
class ToolManifestVersionPatternTest {

    private fun manifestWithVersion(version: String, latest: String? = null) = ToolManifest(
        id = "demo-tool",
        name = "Demo",
        description = "版本号白名单回归",
        version = version,
        latestVersion = latest,
    )

    /** 正向：常见版本写法必须继续被接受，避免修复过度收紧。 */
    @Test
    fun acceptsCommonVersionFormats() {
        listOf(
            "1.0.0",
            "0.21.3",
            "1.2.0-beta.1",
            "v2",
            "2025.10.07",
            "1.0.0+build.42",
            "1_0_0",
        ).forEach { version ->
            ToolManifestValidator.validateAll(listOf(manifestWithVersion(version)))
        }
    }

    /** 反向：路径穿越版本号必须在校验阶段被拒绝。 */
    @Test
    fun rejectsPathTraversalVersions() {
        listOf(
            "../../../shared_prefs/x",
            "..",
            "../..",
            "1.0.0/../../../../cache/x",
            "a/../b",
        ).forEach { version ->
            val failure = runCatching {
                ToolManifestValidator.validateAll(listOf(manifestWithVersion(version)))
            }.exceptionOrNull()
            assertTrue(
                "版本号 $version 应被拒绝，但校验通过了",
                failure is IllegalArgumentException,
            )
        }
    }

    /** 反向：绝对路径与含斜杠的版本号同样必须被拒绝。 */
    @Test
    fun rejectsAbsoluteAndSeparatorVersions() {
        listOf(
            "/etc/passwd",
            "/data/data/top.wkbin.tianxuan/files/x",
            "1.0.0/1.0.1",
            "C:\\Windows",
        ).forEach { version ->
            val failure = runCatching {
                ToolManifestValidator.validateAll(listOf(manifestWithVersion(version)))
            }.exceptionOrNull()
            assertTrue("版本号 $version 应被拒绝", failure is IllegalArgumentException)
        }
    }

    /** 反向：latestVersion 同样进入比较与展示链路，必须受同一白名单约束。 */
    @Test
    fun rejectsUnsafeLatestVersion() {
        val failure = runCatching {
            ToolManifestValidator.validateAll(
                listOf(manifestWithVersion(version = "1.0.0", latest = "../../../shared_prefs/x")),
            )
        }.exceptionOrNull()
        assertTrue("不安全的 latestVersion 应被拒绝", failure is IllegalArgumentException)
    }
}
