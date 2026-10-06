package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.TianXuanWidthClass
import top.wkbin.tianxuan.ui.components.canPinSessionsPane
import top.wkbin.tianxuan.ui.components.rememberWidthClass

/**
 * 宽屏时导航侧栏预留的宽度。
 *
 * 会话栏的可用宽度 = 屏幕宽 − 导航侧栏宽。不直接用屏幕宽度判定，是因为导航侧栏
 * 只在 Medium/Expanded 形态出现；在 Compact 形态下减掉它会凭空少 240dp，
 * 让「刚好能放下两栏」的边界判断失真。
 *
 * 取 Expanded 形态的侧栏宽（240dp）而非最窄的 88dp：宁可低估可用宽度，
 * 也不要在窄一档的平板上把对话区挤到不可用。
 */
private const val NAV_RAIL_RESERVED_DP = 240

/**
 * 会话栏是否应常驻。
 *
 * 抽成纯函数而不是内联在 Composable 里，是为了能被单测直接断言——之前把它写在
 * Composable 内部，测试就无法覆盖，改坏了也不会有测试失败。
 *
 * 判定有两层，缺一不可：
 * 1. 宽度形态支持常驻导航（Compact 一定不行）；
 * 2. 扣掉导航侧栏后的剩余宽度真的放得下「会话栏 + 对话区」两栏。
 *
 * 只看形态不够——Medium 形态下扣掉 240dp 导航侧栏可能只剩 560dp，塞不下会话栏。
 */
internal fun shouldPinSessionsPane(widthClass: TianXuanWidthClass, availableWidthDp: Int): Boolean =
    widthClass.usesPermanentNav && canPinSessionsPane(availableWidthDp)

/**
 * 会话栏可用的宽度 = 屏幕宽 − 导航侧栏预留。
 *
 * 同时供 [ChatSessionsPane] 使用与单测断言，避免「公式改了但判定用的是别处那份」。
 */
internal fun sessionsPaneAvailableWidthDp(screenWidthDp: Int): Int = screenWidthDp - NAV_RAIL_RESERVED_DP

/**
 * 智枢页的会话栏外壳：宽屏常驻（可拖拽分栏），窄屏为抽屉。
 *
 * 这是 ChatScreen 唯一需要知道的会话栏入口。形态判定、宽度收敛、收起状态、
 * 宽度持久化全部在本文件内闭环——ChatScreen 既不感知有几种形态，也不持有分栏状态，
 * 这样它就不会因为分栏功能的每次演进而继续变长。
 */
@Composable
internal fun ChatSessionsPane(
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    sessionRunStates: Map<String, SessionRunState>,
    drawerVisible: Boolean,
    onDrawerVisibleChange: (Boolean) -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenSkills: (() -> Unit)?,
    onOpenRuntime: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val layoutState = rememberSessionsPaneLayoutState(LocalContext.current)
    val widthClass = rememberWidthClass()
    val availableWidthDp = sessionsPaneAvailableWidthDp(LocalConfiguration.current.screenWidthDp)

    // 常驻条件见 shouldPinSessionsPane
    val pinable = shouldPinSessionsPane(widthClass, availableWidthDp)

    if (pinable) {
        Row(modifier = modifier.fillMaxSize()) {
            SessionsPersistentPane(
                sessions = sessions,
                currentSessionId = currentSessionId,
                workspaces = workspaces,
                sessionRunStates = sessionRunStates,
                availableWidthDp = availableWidthDp,
                requestedWidthDp = layoutState.paneWidthDp(),
                collapsed = layoutState.isCollapsed(),
                onWidthChange = layoutState::setPaneWidth,
                onCollapseChange = layoutState::setCollapsed,
                onSwitch = onSwitch,
                onNew = onNew,
                onCreateInWorkspace = onCreateInWorkspace,
                onDelete = onDelete,
                onRename = onRename,
                onOpenSkills = onOpenSkills,
                onOpenRuntime = onOpenRuntime,
            )
            content(Modifier.weight(1f).fillMaxHeight())
        }
        return
    }

    SessionsSideDrawer(
        visible = drawerVisible,
        sessions = sessions,
        currentSessionId = currentSessionId,
        workspaces = workspaces,
        sessionRunStates = sessionRunStates,
        onDismiss = { onDrawerVisibleChange(false) },
        // 窄屏切完会话必须收起抽屉，否则它会盖在新会话上方挡住输入框
        onSwitch = { id -> onDrawerVisibleChange(false); onSwitch(id) },
        onNew = { onDrawerVisibleChange(false); onNew() },
        onCreateInWorkspace = { ws -> onDrawerVisibleChange(false); onCreateInWorkspace(ws) },
        onDelete = onDelete,
        onRename = onRename,
        onOpenSkills = onOpenSkills?.let { open -> { onDrawerVisibleChange(false); open() } },
        onOpenRuntime = onOpenRuntime?.let { open -> { onDrawerVisibleChange(false); open() } },
    )
    content(modifier.fillMaxSize())
}