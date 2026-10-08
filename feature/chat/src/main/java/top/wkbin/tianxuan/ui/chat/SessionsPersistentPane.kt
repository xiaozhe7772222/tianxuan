package top.wkbin.tianxuan.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.feature.chat.R
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimePaneDivider
import top.wkbin.tianxuan.ui.components.TianXuanPaneMetrics
import top.wkbin.tianxuan.ui.components.resolveSessionsPaneWidth

/** 收起后那条窄提示条的测试标签。 */
const val SESSIONS_PANE_RAIL_TEST_TAG: String = "tianxuan-sessions-pane-rail"

/**
 * 平板常驻会话栏。
 *
 * 与抽屉的唯一区别是容器：本组件常驻在导航侧栏右侧、对话区左侧，中间一条可拖拽分隔条。
 * 内容仍然复用 [SessionsPaneContent]，因此两种形态下会话列表的表现完全一致。
 *
 * @param availableWidthDp 当前可用宽度（已扣除导航侧栏），用于收敛用户拖出的宽度。
 * @param requestedWidthDp 用户上次拖定的宽度，0 表示未拖过。
 * @param collapsed 是否处于收起状态。
 * @param onWidthChange 拖拽结束时上报新宽度（dp），由调用方持久化。
 * @param onCollapseChange 收起/展开状态变化。
 */
@Composable
internal fun SessionsPersistentPane(
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    sessionRunStates: Map<String, SessionRunState>,
    availableWidthDp: Int,
    requestedWidthDp: Int,
    collapsed: Boolean,
    onWidthChange: (Int) -> Unit,
    onCollapseChange: (Boolean) -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenSkills: (() -> Unit)?,
    onOpenRuntime: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val resolvedWidth = resolveSessionsPaneWidth(
        requestedDp = if (requestedWidthDp > 0) requestedWidthDp else TianXuanPaneMetrics.SESSIONS_PANE_DEFAULT_DP,
        availableWidthDp = availableWidthDp,
    )

    if (collapsed) {
        CollapsedRail(onExpand = { onCollapseChange(false) }, modifier = modifier)
        return
    }

    val density = LocalDensity.current
    // 拖拽过程中的实时宽度：放在本地 state 里，松手才上报，避免每帧写 DataStore
    var liveWidthDp by remember(resolvedWidth) { mutableStateOf(resolvedWidth) }
    var dragWidthDp by remember { mutableStateOf<Int?>(null) }
    val effectiveWidth = dragWidthDp ?: liveWidthDp

    Row(modifier = modifier.fillMaxHeight()) {
        Surface(
            modifier = Modifier
                .width(effectiveWidth.dp)
                .fillMaxHeight()
                .testTag(SESSIONS_PANE_TEST_TAG),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        ) {
            SessionsPaneContent(
                sessions = sessions,
                currentSessionId = currentSessionId,
                workspaces = workspaces,
                sessionRunStates = sessionRunStates,
                collapseSlot = { CollapseButton(onClick = { onCollapseChange(true) }) },
                onSwitch = onSwitch,
                onNew = onNew,
                onCreateInWorkspace = onCreateInWorkspace,
                onDelete = onDelete,
                onRename = onRename,
                onOpenSkills = onOpenSkills,
                onOpenRuntime = onOpenRuntime,
                afterAction = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        RuntimePaneDivider(
            onDragDelta = { deltaPx ->
                val deltaDp = with(density) { deltaPx.toDp() }.value.toInt()
                val next = resolveSessionsPaneWidth(effectiveWidth + deltaDp, availableWidthDp)
                dragWidthDp = next
                liveWidthDp = next
            },
            onDragEnd = {
                val settled = dragWidthDp
                dragWidthDp = null
                if (settled != null && settled != requestedWidthDp) {
                    onWidthChange(settled)
                }
            },
        )
    }
}

/** 收起状态：一条窄提示条，提示「会话栏已收起」并可点开。 */
@Composable
private fun CollapsedRail(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(44.dp)
            .fillMaxHeight()
            .testTag(SESSIONS_PANE_RAIL_TEST_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RuntimeIcon(
                name = RuntimeIconName.ChevronRight,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.chat_drawer_collapsed_rail),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 顶部「收起会话栏」按钮。 */
@Composable
private fun CollapseButton(onClick: () -> Unit) {
    RuntimeIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .testTag(SESSIONS_PANE_COLLAPSE_TEST_TAG),
    ) {
        RuntimeIcon(
            name = RuntimeIconName.ChevronRight,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}