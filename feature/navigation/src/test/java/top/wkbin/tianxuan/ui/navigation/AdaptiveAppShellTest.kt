package top.wkbin.tianxuan.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.tianxuan.ui.components.NAV_ITEM_TEST_TAG
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.TianXuanWidthClass

/**
 * 自适应外壳的渲染测试。
 *
 * 只测纯函数（宽度分型）不够：那只证明判定对，证明不了「判定结果真的改变了
 * 渲染结构」。宽屏下若侧栏没被渲染，函数测试全绿而用户仍看到底栏——这正是
 * 平板改造最容易漏的一环，故此处断言实际渲染出的节点。
 *
 * 用 testTag 计数而非中文文案：文案会随星象体系调整而变，
 * 拿「星图/天枢」写死断言，改名后测试会因无关原因失败。
 *
 * 用 [RobolectricTestRunner] 而非 AndroidJUnit4：本项目 daemon 用 JDK 25，
 * AndroidJUnit4 的初始化路径在本机JRE 上会抛
 * 「Failed to interact with raw FileDescriptor internals」。sdk 固定 34，
 * 与仓库里其他 Robolectric 测试一致，避免跨版本行为漂移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveAppShellTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shell(widthClass: TianXuanWidthClass) {
        compose.setContent {
            AdaptiveAppShell(
                selected = MainDestination.Home,
                onNavigate = {},
                widthClass = widthClass,
            ) { paneModifier ->
                Box(paneModifier.testTag(TAG_CONTENT)) { Text("内容区") }
            }
        }
    }

    private fun navItemCount(): Int =
        compose.onAllNodesWithTag(TAG_NAV_ITEM).fetchSemanticsNodes().size

    @Test
    fun `宽屏渲染四个常驻导航项`() {
        shell(TianXuanWidthClass.Expanded)
        assertEquals(4, navItemCount())
    }

    @Test
    fun `中等宽度同样渲染四个常驻导航项`() {
        // 11 寸平板横屏落在中等区间，也必须用侧栏而非底栏
        shell(TianXuanWidthClass.Medium)
        assertEquals(4, navItemCount())
    }

    @Test
    fun `紧凑形态不渲染侧栏`() {
        shell(TianXuanWidthClass.Compact)
        assertEquals(0, navItemCount())
    }

@Test
fun `内容区在三种形态下都渲染`() {
        // 三种形态各写一条测试：同一个 ComposeTestRule 实例只能 setContent 一次，
        // 而循环里另建 rule 会缺 ActivityScenarioRule 支撑而报
        // 「getScenario 返回 null」。写成三条独立用例最直白，也避免时序陷阱。
        assertContentRendered(TianXuanWidthClass.Compact)
    }

    @Test
    fun `内容区在中等宽度下渲染`() {
        assertContentRendered(TianXuanWidthClass.Medium)
    }

    @Test
    fun `内容区在展开宽度下渲染`() {
        assertContentRendered(TianXuanWidthClass.Expanded)
    }

    /** 每个用例各自 setContent 一次，故可安全复用类级 rule */
    private fun assertContentRendered(widthClass: TianXuanWidthClass) {
        compose.setContent {
            AdaptiveAppShell(
                selected = MainDestination.Home,
                onNavigate = {},
                widthClass = widthClass,
            ) { paneModifier ->
                Box(paneModifier.testTag(TAG_CONTENT)) { Text("内容区") }
            }
        }
        compose.onNodeWithTag(TAG_CONTENT).assertIsDisplayed()
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals("导航项数量不符", expected, actual)
    }

    private companion object {
        const val TAG_CONTENT = "adaptive-content"
        const val TAG_NAV_ITEM = NAV_ITEM_TEST_TAG
    }
}