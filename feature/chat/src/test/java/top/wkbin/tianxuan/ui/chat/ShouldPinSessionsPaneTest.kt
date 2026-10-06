package top.wkbin.tianxuan.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.tianxuan.ui.components.TianXuanPaneMetrics
import top.wkbin.tianxuan.ui.components.TianXuanWidthClass

/**
 * 会话栏形态判定的边界测试。
 *
 * 这些判定决定用户看到的是「常驻三栏」还是「抽屉」——判错的表现是平板上对话区被挤扁，
 * 或手机上莫名出现一条占地方却拖不动的窄栏。所以每条分支都钉死具体宽度数值。
 */
class ShouldPinSessionsPaneTest {

    private val threshold = TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP +
        TianXuanPaneMetrics.DIVIDER_WIDTH_DP +
        TianXuanPaneMetrics.MAIN_PANE_MIN_DP

    @Test
    fun `紧凑形态一律不常驻即便窗口很宽`() {
        // 手机在横屏或折叠屏展开后宽度也可能超过门槛，但形态仍是 Compact
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Compact, 2000))
    }

    @Test
    fun `中等形态宽度足够时仍常驻`() {
        // 800dp 手机横屏扣掉侧栏 240dp 后剩 560dp < 门槛 608dp
        assertEquals(560, sessionsPaneAvailableWidthDp(800))
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Medium, sessionsPaneAvailableWidthDp(800)))
    }

    @Test
    fun `中等形态足够宽时判定常驻`() {
        // 1000dp 平板扣侧栏后剩 760dp > 608dp
        assertEquals(760, sessionsPaneAvailableWidthDp(1000))
        assertTrue(shouldPinSessionsPane(TianXuanWidthClass.Medium, 760))
    }

    @Test
    fun `展开形态在典型平板尺寸都常驻`() {
        // 11 寸横屏约 1280dp、13 寸横屏约 1366dp/1600dp
        for (screen in listOf(1280, 1366, 1600, 1920, 2560)) {
            assertTrue(
                "screen=$screen 应常驻",
                shouldPinSessionsPane(TianXuanWidthClass.Expanded, sessionsPaneAvailableWidthDp(screen)),
            )
        }
    }

    @Test
    fun `门槛两侧判定相反`() {
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Expanded, threshold - 1))
        assertTrue(shouldPinSessionsPane(TianXuanWidthClass.Expanded, threshold))
    }

    @Test
    fun `形态与宽度两条件缺一不可`() {
        // 形态够宽但宽度不足 → 不常驻
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Expanded, threshold - 1))
        // 宽度够但形态不支持 → 不常驻
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Compact, threshold + 1000))
    }

    @Test
    fun `负宽度不会误判为常驻`() {
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Expanded, -100))
        assertFalse(shouldPinSessionsPane(TianXuanWidthClass.Expanded, 0))
    }

    @Test
    fun `可用宽度恒为屏幕宽减去侧栏预留`() {
        for (screen in listOf(320, 600, 800, 1280, 2560)) {
            assertEquals(screen - 240, sessionsPaneAvailableWidthDp(screen))
        }
    }

    @Test
    fun `展开形态在全部合理窗口宽度下都常驻`() {
        // 平板上不应出现「莫名其妙只剩抽屉」的情形：只要形态是展开就该常驻
        for (screen in listOf(1000, 1200, 1280, 1366, 1600, 1920, 2048, 2560)) {
            assertTrue(
                "screen=$screen 展开形态不该退回抽屉",
                shouldPinSessionsPane(TianXuanWidthClass.Expanded, sessionsPaneAvailableWidthDp(screen)),
            )
        }
    }
}