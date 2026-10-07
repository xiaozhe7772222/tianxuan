package top.wkbin.tianxuan.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地插件「取哪个版本」的选择逻辑回归。
 *
 * 背景：`ToolRegistry.loadLocalManifests()` 原先用 `maxByOrNull { it.name }` 在
 * `plugins/<id>/<version>/` 的候选目录里挑一个，而 `String` 的 `Comparable` 是**字典序**：
 *
 *   max("1.9.0", "1.10.0") == "1.9.0"      // 错！应为 1.10.0
 *   max("1.9.0", "1.11.0") == "1.9.0"      // 错！应为 1.11.0
 *
 * 后果不是崩溃而是**静默取旧**：用户导入并成功安装了 1.10.0，`localPayloadRoot()` 仍然
 * 指向 1.9.0 的 payload 目录，安装出来的永远是旧内容，且没有任何错误提示。
 * 这类"没报错所以以为没问题"的缺陷只能靠版本比较本身的正反向断言兜住。
 */
class ToolVersionOrderingTest {

    @Test
    fun numericSegmentsBeatLexicographicOrder() {
        assertTrue("1.10.0 必须大于 1.9.0", compareToolVersions("1.10.0", "1.9.0") > 0)
        assertTrue("1.11.0 必须大于 1.9.0", compareToolVersions("1.11.0", "1.9.0") > 0)
        assertTrue("2.0.0 必须大于 1.99.99", compareToolVersions("2.0.0", "1.99.99") > 0)
        assertTrue("0.21.10 必须大于 0.21.3", compareToolVersions("0.21.10", "0.21.3") > 0)
    }

    @Test
    fun picksHighestVersionFromCandidateDirectories() {
        val candidates = listOf("1.9.0", "1.10.0", "1.2.0", "1.10.1")
        val picked = candidates.maxWithOrNull { a, b -> compareToolVersions(a, b) }
        assertEquals("1.10.1", picked)
    }

    @Test
    fun missingSegmentsCompareAsZero() {
        assertEquals(0, compareToolVersions("1.0", "1.0.0"))
        assertTrue(compareToolVersions("1.0.1", "1.0") > 0)
    }

    /** 无法提取数字时退化为字典序，但必须保持自反与反对称，绝不抛异常。 */
    @Test
    fun nonNumericVersionsFallBackToLexicographicWithoutThrowing() {
        assertEquals(0, compareToolVersions("beta", "beta"))
        assertTrue("'beta' 与 'alpha' 应可比较", compareToolVersions("beta", "alpha") > 0)
    }

    /** 前缀与后缀（v / -rc.1 / +build）不应破坏数值部分的比较结论。 */
    @Test
    fun prefixesAndSuffixesDoNotBreakNumericOrdering() {
        assertTrue(compareToolVersions("v2", "v10") < 0)
        assertTrue(compareToolVersions("2.0.0-rc.1", "1.9.0") > 0)
    }
}
