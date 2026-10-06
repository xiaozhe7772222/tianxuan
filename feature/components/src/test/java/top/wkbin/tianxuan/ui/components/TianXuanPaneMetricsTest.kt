package top.wkbin.tianxuan.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分栏宽度收敛规则的边界测试。
 *
 * 这类规则最容易在「用户拖过之后换了个窗口宽度」「平板分屏」等场景下出偏差，
 * 因此每个分支都钉一个具体数值，而不是只测大小关系。
 */
class TianXuanPaneMetricsTest {

    @Test
    fun `默认宽度落在收敛区间内且保持原值`() {
        assertEquals(
            TianXuanPaneMetrics.SESSIONS_PANE_DEFAULT_DP,
            resolveSessionsPaneWidth(TianXuanPaneMetrics.SESSIONS_PANE_DEFAULT_DP, 1280),
        )
    }

    @Test
    fun `小于下界的宽度被抬到下界`() {
        assertEquals(
            TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP,
            resolveSessionsPaneWidth(10, 1280),
        )
    }

    @Test
    fun `负宽度也被抬到下界`() {
        assertEquals(
            TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP,
            resolveSessionsPaneWidth(-500, 1280),
        )
    }

    @Test
    fun `超过上限的宽度被压到上限`() {
        assertEquals(
            TianXuanPaneMetrics.SESSIONS_PANE_MAX_DP,
            resolveSessionsPaneWidth(2000, 4000),
        )
    }

    @Test
    fun `窗口变窄时以主对话区最小宽为准而非固定上限`() {
        // 窗口 700dp：扣掉分隔条 8dp 后剩 692dp，减去主区最小 360dp = 332dp 上限
        assertEquals(332, resolveSessionsPaneWidth(460, 700))
    }

    @Test
    fun `窗口过窄返回零表示不该渲染常驻栏`() {
        // 放不下两栏：360dp 主区 + 8dp 分隔条 + 240dp 会话栏 = 608dp 是门槛
        assertEquals(0, resolveSessionsPaneWidth(320, 300))
        assertEquals(0, resolveSessionsPaneWidth(320, 607))
    }

    @Test
    fun `刚好卡住门槛时返回一个合法宽度`() {
        // 608dp：ceiling = 608 - 8 - 360 = 240，正好等于下界
        assertEquals(TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP, resolveSessionsPaneWidth(320, 608))
    }

    @Test
    fun `收敛结果永远落在最小与最大之间`() {
        val samples = listOf(0, 1, 239, 240, 241, 320, 459, 460, 461, 5000)
        for (available in listOf(368, 400, 600, 607, 608, 700, 800, 1024, 1280, 1600, 2560)) {
            for (requested in samples) {
                val resolved = resolveSessionsPaneWidth(requested, available)
                if (resolved == 0) {
                    // 只有放不下两栏才允许 0，且必须与常驻判定一致
                    assertFalse(
                        "available=$available 不该返回 0",
                        canPinSessionsPane(available),
                    )
                    continue
                }
                assertTrue(
                    "requested=$requested available=$available 收敛到 $resolved 越界",
                    resolved in TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP..TianXuanPaneMetrics.SESSIONS_PANE_MAX_DP,
                )
                // 主对话区不能被挤破
                assertTrue(
                    "主区被压到 ${available - TianXuanPaneMetrics.DIVIDER_WIDTH_DP - resolved}dp",
                    available - TianXuanPaneMetrics.DIVIDER_WIDTH_DP - resolved >=
                        TianXuanPaneMetrics.MAIN_PANE_MIN_DP,
                )
            }
        }
    }

    @Test
    fun `返回零与常驻判定永远同源`() {
        // 这一条是防回归的核心：两处判定若各写各的，早晚会出现「判定能常驻但宽度算出 0」
        // 或反之的窗口宽度，表现为会话栏空白或对话区被压扁。
        for (available in 0..1600 step 7) {
            val resolved = resolveSessionsPaneWidth(320, available)
            assertEquals(
                "available=$available 两个判定不一致",
                canPinSessionsPane(available),
                resolved > 0,
            )
        }
    }

    @Test
    fun `常驻判定在临界值两侧相反`() {
        val threshold = TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP +
            TianXuanPaneMetrics.DIVIDER_WIDTH_DP +
            TianXuanPaneMetrics.MAIN_PANE_MIN_DP
        assertFalse(canPinSessionsPane(threshold - 1))
        assertTrue(canPinSessionsPane(threshold))
        assertTrue(canPinSessionsPane(threshold + 500))
    }

    @Test
    fun `常见平板宽度都判定为可常驻`() {
        // 11 寸横屏约 1280dp、13 寸横屏约 1366dp/1600dp；扣掉导航侧栏 240dp 后仍需够两栏
        for (available in listOf(1040, 1126, 1360, 1600)) {
            assertTrue("available=$available 应可常驻", canPinSessionsPane(available))
        }
    }

    @Test
    fun `手机宽度判定为不可常驻`() {
        for (available in listOf(320, 360, 411, 599)) {
            assertFalse("available=$available 不应可常驻", canPinSessionsPane(available))
        }
    }

    @Test
    fun `触摸热区显著宽于视觉宽度`() {
        // 平板上手指命中 8dp 细线不现实，热区必须明显更宽
        assertTrue(
            TianXuanPaneMetrics.DIVIDER_TOUCH_WIDTH_DP >= TianXuanPaneMetrics.DIVIDER_WIDTH_DP * 2,
        )
    }
}