package top.wkbin.tianxuan.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.runtime.ProjectType
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.TianXuanPaneMetrics

/** 按项目划分的会话组数据结构。 */
data class ProjectSessionGroup(
    val projectName: String,
    val workspacePath: String,
    val projectType: ProjectType,
    val sessions: List<HarnessSessionEntity>,
)

/** 会话分区结果：项目分组与未关联的最近会话。 */
data class SessionPartitionResult(
    val projectGroups: List<ProjectSessionGroup>,
    val recentSessions: List<HarnessSessionEntity>,
)

/**
 * 将全部会话与工作区进行归集与分区：
 * 1. 关联了工作区/项目的会话归类入对应项目分组（按 updatedAt 倒序）。
 * 2. 未关联工作区的纯沙箱会话归入「最近」（按 updatedAt 倒序）。
 */
fun partitionSessions(
    sessions: List<HarnessSessionEntity>,
    workspaces: List<WorkspaceProject>,
): SessionPartitionResult {
    val (withWs, withoutWs) = sessions.partition { it.workspace.isNotBlank() }
    val recentSessions = withoutWs.sortedByDescending { it.updatedAt }

    val sessionsByWs = withWs.groupBy { it.workspace.trimEnd('/') }
    val handledWsPaths = mutableSetOf<String>()
    val projectGroups = mutableListOf<ProjectSessionGroup>()

    for (ws in workspaces) {
        val normPath = ws.linuxPath.trimEnd('/')
        handledWsPaths.add(normPath)
        val projSessions = sessionsByWs[normPath].orEmpty().sortedByDescending { it.updatedAt }
        projectGroups.add(
            ProjectSessionGroup(
                projectName = ws.name,
                workspacePath = ws.linuxPath,
                projectType = ws.projectType,
                sessions = projSessions,
            ),
        )
    }

    // 容错：处理工作区已被删除但仍保留 workspace 路径的历史孤立会话
    for ((wsPath, orphanSessions) in sessionsByWs) {
        if (!handledWsPaths.contains(wsPath)) {
            val extractedName = wsPath.substringAfterLast('/').ifBlank { wsPath }
            projectGroups.add(
                ProjectSessionGroup(
                    projectName = extractedName,
                    workspacePath = wsPath,
                    projectType = ProjectType.GENERAL,
                    sessions = orphanSessions.sortedByDescending { it.updatedAt },
                ),
            )
        }
    }

    val sortedProjectGroups = projectGroups.sortedWith(
        compareByDescending<ProjectSessionGroup> { it.sessions.firstOrNull()?.updatedAt ?: 0L }
            .thenBy { it.projectName.lowercase() },
    )

    return SessionPartitionResult(
        projectGroups = sortedProjectGroups,
        recentSessions = recentSessions,
    )
}

/**
 * 窄屏形态：侧边弹窗式会话抽屉。
 *
 * 本文件只负责「弹窗外壳」——遮罩、进退场动画、关闭时机；内容全部委托给
 * [SessionsPaneContent]，与平板常驻栏共用同一份实现。
 */
@Composable
internal fun SessionsSideDrawer(
    visible: Boolean,
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    sessionRunStates: Map<String, SessionRunState>,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenSkills: (() -> Unit)? = null,
    onOpenRuntime: (() -> Unit)? = null,
) {
    if (!visible) return

    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val handleDismiss: () -> Unit = {
        coroutineScope.launch {
            isVisible = false
            delay(180)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler { handleDismiss() }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = handleDismiss,
                        ),
                )
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(180),
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(
                            min = TianXuanPaneMetrics.SESSIONS_DRAWER_MIN_DP.dp,
                            max = TianXuanPaneMetrics.SESSIONS_DRAWER_MAX_DP.dp,
                        )
                        .fillMaxWidth(0.85f)
                        .testTag(SESSIONS_PANE_TEST_TAG),
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    SessionsPaneContent(
                        sessions = sessions,
                        currentSessionId = currentSessionId,
                        workspaces = workspaces,
                        sessionRunStates = sessionRunStates,
                        collapseSlot = { DrawerCloseButton(onClick = handleDismiss) },
                        onSwitch = onSwitch,
                        onNew = onNew,
                        onCreateInWorkspace = onCreateInWorkspace,
                        onDelete = onDelete,
                        onRename = onRename,
                        onOpenSkills = onOpenSkills,
                        onOpenRuntime = onOpenRuntime,
                        afterAction = handleDismiss,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}

/** 抽屉右上角关闭按钮。 */
@Composable
private fun DrawerCloseButton(onClick: () -> Unit) {
    RuntimeIconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        RuntimeIcon(
            name = RuntimeIconName.Close,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}