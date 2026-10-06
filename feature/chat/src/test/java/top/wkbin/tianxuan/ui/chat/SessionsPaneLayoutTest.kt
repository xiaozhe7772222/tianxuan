package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.runtime.ProjectType
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.PANE_DIVIDER_TEST_TAG
import top.wkbin.tianxuan.ui.components.TianXuanPaneMetrics
import top.wkbin.tianxuan.ui.components.resolveSessionsPaneWidth

/** 对话区占位标签。 */
private const val MAIN_TAG = "chat-main-slot"

/**
 * 会话栏形态的渲染测试。
 *
 * 直接渲染 [SessionsPersistentPane] 与抽屉的判定逻辑，而不是渲染整个 ChatScreen：
 * 后者要装配 ViewModel、Room、SSE，测试会慢到失去意义；这里守住的是真正容易回归的那条边界——
 * 「宽屏有常驻栏 + 有分隔条，窄屏两者都没有」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionsPaneLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val sessions = listOf(
        HarnessSessionEntity(
            id = "s1",
            title = "重构沙箱启动器",
            createdAt = 0L,
            updatedAt = 1_000L,
            modelId = null,
            workspace = "/root/work",
        ),
    )

    private val workspaces = listOf(
        WorkspaceProject(
            name = "天玄",
            path = "/root/work",
            linuxPath = "/root/work",
            sizeBytes = 0L,
            projectType = ProjectType.GENERAL,
        ),
    )

    private fun countNodes(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Composable
    private fun PersistentPane(collapsed: Boolean = false) {
        Box(modifier = Modifier.fillMaxSize().testTag(MAIN_TAG)) {
            SessionsPersistentPane(
                sessions = sessions,
                currentSessionId = "s1",
                workspaces = workspaces,
                sessionRunStates = mapOf("s1" to SessionRunState.IDLE),
                availableWidthDp = 1280,
                requestedWidthDp = 0,
                collapsed = collapsed,
                onWidthChange = {},
                onCollapseChange = {},
                onSwitch = {},
                onNew = {},
                onCreateInWorkspace = {},
                onDelete = {},
                onRename = { _, _ -> },
                onOpenSkills = {},
                onOpenRuntime = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun `常驻栏与分隔条同时渲染`() {
        compose.setContent { PersistentPane() }
        assertEquals(1, countNodes(SESSIONS_PANE_TEST_TAG))
        assertEquals(1, countNodes(PANE_DIVIDER_TEST_TAG))
    }

    @Test
    fun `常驻栏渲染项目分区与最近分区`() {
        compose.setContent { PersistentPane() }
        compose.onNodeWithTag(SESSIONS_PANE_TEST_TAG).assertIsDisplayed()
        // 会话已关联工作区，故只在项目分区出现一次
        compose.onNodeWithText("重构沙箱启动器").assertIsDisplayed()
    }

    @Test
    fun `收起后不再渲染分隔条只剩窄提示条`() {
        compose.setContent { PersistentPane(collapsed = true) }
        assertEquals(0, countNodes(PANE_DIVIDER_TEST_TAG))
        assertEquals(0, countNodes(SESSIONS_PANE_TEST_TAG))
        assertEquals(1, countNodes(SESSIONS_PANE_RAIL_TEST_TAG))
    }

    @Test
    fun `常驻栏的收起按钮可被渲染`() {
        compose.setContent { PersistentPane() }
        compose.onNodeWithTag(SESSIONS_PANE_COLLAPSE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun `宽度收敛遵守主区不被挤破的硬约束`() {
        // 契约：收敛函数是纯数学，不含「未拖过则回落默认值」的业务判断
        // （那件事在 SessionsPersistentPane 里做）。所以 requested=0 会落到下界。
        assertEquals(TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP, resolveSessionsPaneWidth(0, 1280))

        // 门槛以下返回 0，宿主据此改用抽屉
        val tooNarrow = TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP +
            TianXuanPaneMetrics.DIVIDER_WIDTH_DP +
            TianXuanPaneMetrics.MAIN_PANE_MIN_DP - 1
        assertEquals(0, resolveSessionsPaneWidth(320, tooNarrow))

        // 宽裕窗口下上限由 MAX 决定，而非无限放宽
        assertEquals(TianXuanPaneMetrics.SESSIONS_PANE_MAX_DP, resolveSessionsPaneWidth(9999, 1280))
    }

    @Test
    fun `会话栏最小宽度足以容纳会话项`() {
        // 会话项里有 24dp 的两个快捷按钮 + 7dp 状态点 + 内边距，下界不能退到导航侧栏最窄档以下
        assertEquals(240, TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP)
    }
}