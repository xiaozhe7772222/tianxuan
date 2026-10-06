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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.feature.chat.R
import top.wkbin.tianxuan.runtime.ProjectType
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeFilledTonalButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.TianXuanBrandBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@Composable
private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> stringResource(R.string.chat_time_just_now)
        diff < 3600_000L -> stringResource(R.string.chat_time_minutes_ago, (diff / 60_000L).coerceAtLeast(1))
        diff < 86400_000L -> stringResource(R.string.chat_time_hours_ago, (diff / 3600_000L).coerceAtLeast(1))
        diff < 86400_000L * 2 -> stringResource(R.string.chat_time_yesterday)
        diff < 86400_000L * 7 -> stringResource(R.string.chat_time_days_ago, (diff / 86400_000L).coerceAtLeast(1))
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 侧边弹窗形式的会话管理与新建抽屉（参考千问办公侧边栏风格）：
 * - 屏幕左侧展开，含背景遮罩与进退场滑动动画。
 * - 顶部置顶醒目「新任务 / 新建会话」大按钮与快捷扩展入口。
 * - 「项目」分组展示关联工作区工程，支持展开/折叠与单项目一键「+」新建会话。
 * - 「最近」分组展示未关联工作区的会话，按最近修改时间倒序排列。
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

    // 重命名与删除二次确认状态
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    // 默认展开包含当前活跃会话的项目，若无则默认展开有会话的项目
    val partition = remember(sessions, workspaces) { partitionSessions(sessions, workspaces) }
    var expandedProjects by rememberSaveable {
        val initialExpanded = partition.projectGroups
            .filter { group -> group.sessions.any { it.id == currentSessionId } || group.sessions.isNotEmpty() }
            .map { it.workspacePath }
            .toSet()
        mutableStateOf(initialExpanded)
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
            // 1. 半透明暗色背景遮罩（点击关闭）
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

            // 2. 右侧侧边抽屉面板（从右往左弹出，靠近屏幕右侧）
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
                        .widthIn(min = 280.dp, max = 340.dp)
                        .fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        // 顶部 Header：品牌 Badge + 标题 + 会话统计 + 关闭按钮
                        DrawerHeader(
                            totalSessionsCount = sessions.size,
                            onClose = handleDismiss,
                        )

                        // 顶部醒目的「新任务 / 新建会话」大按钮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            RuntimeButton(
                                onClick = {
                                    handleDismiss()
                                    onNew()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    RuntimeIcon(
                                        name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Edit,
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text(
                                        text = stringResource(R.string.chat_drawer_new_task),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }

                        // 快捷扩展与监控入口（复刻千问侧栏辅助导航，带防截断内边距）
                        if (onOpenSkills != null || onOpenRuntime != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (onOpenSkills != null) {
                                    RuntimeFilledTonalButton(
                                        onClick = {
                                            handleDismiss()
                                            onOpenSkills()
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            RuntimeIcon(
                                                name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Extension,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                            Text(
                                                text = stringResource(R.string.chat_drawer_quick_skills),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                if (onOpenRuntime != null) {
                                    RuntimeFilledTonalButton(
                                        onClick = {
                                            handleDismiss()
                                            onOpenRuntime()
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            RuntimeIcon(
                                                name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Logs,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                            Text(
                                                text = stringResource(R.string.chat_drawer_quick_runtime),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )

                        // 滚动列表内容：项目分组 + 最近分组
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 分区 1：项目 (Projects)
                            ProjectSection(
                                projectGroups = partition.projectGroups,
                                currentSessionId = currentSessionId,
                                sessionRunStates = sessionRunStates,
                                expandedProjects = expandedProjects,
                                onToggleExpand = { path ->
                                    expandedProjects = if (expandedProjects.contains(path)) {
                                        expandedProjects - path
                                    } else {
                                        expandedProjects + path
                                    }
                                },
                                onSwitch = { id ->
                                    handleDismiss()
                                    onSwitch(id)
                                },
                                onCreateInProject = { group ->
                                    val matchedWs = workspaces.firstOrNull { it.linuxPath == group.workspacePath }
                                        ?: WorkspaceProject(
                                            name = group.projectName,
                                            path = group.workspacePath,
                                            linuxPath = group.workspacePath,
                                            sizeBytes = 0L,
                                            projectType = group.projectType,
                                        )
                                    handleDismiss()
                                    onCreateInWorkspace(matchedWs)
                                },
                                onRename = { id -> renameTargetId = id },
                                onDelete = { id -> deleteTargetId = id },
                            )

                            // 分区 2：最近 (Recent)
                            RecentSection(
                                recentSessions = partition.recentSessions,
                                currentSessionId = currentSessionId,
                                sessionRunStates = sessionRunStates,
                                onSwitch = { id ->
                                    handleDismiss()
                                    onSwitch(id)
                                },
                                onRename = { id -> renameTargetId = id },
                                onDelete = { id -> deleteTargetId = id },
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    // 重命名会话弹窗
    val renameTarget = renameTargetId?.let { id -> sessions.firstOrNull { it.id == id } }
    renameTarget?.let { target ->
        RenameSessionDialog(
            currentTitle = target.title,
            onDismiss = { renameTargetId = null },
            onRename = { newTitle ->
                onRename(target.id, newTitle)
                renameTargetId = null
            },
        )
    }

    // 删除会话二次确认弹窗
    deleteTargetId?.let { targetId ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text(stringResource(R.string.chat_delete_session_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.chat_delete_session_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        deleteTargetId = null
                        onDelete(targetId)
                    },
                ) {
                    Text(stringResource(R.string.chat_confirm_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                top.wkbin.tianxuan.ui.components.RuntimeTextButton(onClick = { deleteTargetId = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
}

/** 抽屉顶部标题行。 */
@Composable
private fun DrawerHeader(
    totalSessionsCount: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TianXuanBrandBadge(size = 24.dp)
            Text(
                text = stringResource(R.string.chat_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_session_count, totalSessionsCount),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        RuntimeIconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp),
        ) {
            RuntimeIcon(
                name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Close,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 项目分区。 */
@Composable
private fun ProjectSection(
    projectGroups: List<ProjectSessionGroup>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    expandedProjects: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSwitch: (String) -> Unit,
    onCreateInProject: (ProjectSessionGroup) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_projects),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${projectGroups.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (projectGroups.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_projects),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            projectGroups.forEach { group ->
                val isExpanded = expandedProjects.contains(group.workspacePath)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isExpanded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                            else Color.Transparent,
                        ),
                ) {
                    // 项目文件夹行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleExpand(group.workspacePath) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RuntimeIcon(
                            name = if (isExpanded) top.wkbin.tianxuan.ui.components.RuntimeIconName.FolderOpen
                            else top.wkbin.tianxuan.ui.components.RuntimeIconName.Folder,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = group.projectName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (group.sessions.isNotEmpty()) {
                            Text(
                                text = "${group.sessions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }

                        // 单项目快捷新建会话入口
                        RuntimeIconButton(
                            onClick = { onCreateInProject(group) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            RuntimeIcon(
                                name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Plus,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        RuntimeIcon(
                            name = if (isExpanded) top.wkbin.tianxuan.ui.components.RuntimeIconName.ChevronDown
                            else top.wkbin.tianxuan.ui.components.RuntimeIconName.ChevronRight,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 展开的会话子项
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (group.sessions.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_drawer_no_sessions_in_project),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            } else {
                                group.sessions.forEach { session ->
                                    SessionDrawerItem(
                                        session = session,
                                        isCurrent = session.id == currentSessionId,
                                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                                        onSwitch = { onSwitch(session.id) },
                                        onRename = { onRename(session.id) },
                                        onDelete = { onDelete(session.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 最近分区（未关联项目的会话）。 */
@Composable
private fun RecentSection(
    recentSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.chat_drawer_recent),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${recentSessions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (recentSessions.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.chat_drawer_no_recent_sessions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                recentSessions.forEach { session ->
                    SessionDrawerItem(
                        session = session,
                        isCurrent = session.id == currentSessionId,
                        runState = sessionRunStates[session.id] ?: SessionRunState.IDLE,
                        onSwitch = { onSwitch(session.id) },
                        onRename = { onRename(session.id) },
                        onDelete = { onDelete(session.id) },
                    )
                }
            }
        }
    }
}

/** 单条会话项。 */
@Composable
private fun SessionDrawerItem(
    session: HarnessSessionEntity,
    isCurrent: Boolean,
    runState: SessionRunState,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dotColor = when (runState) {
        SessionRunState.RUNNING -> Color(0xFFF59E0B)
        SessionRunState.WAITING_APPROVAL -> Color(0xFF8B5CF6)
        SessionRunState.FAILED -> Color(0xFFEF4444)
        SessionRunState.COMPLETED -> Color(0xFF10B981)
        SessionRunState.IDLE -> Color(0xFF10B981)
    }

    val timeLabel = formatSessionTime(session.updatedAt)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh
        else Color.Transparent,
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSwitch)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 运行状态小圆点
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isCurrent) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.chat_current),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // 快捷重命名按钮
            RuntimeIconButton(
                onClick = onRename,
                modifier = Modifier.size(24.dp),
            ) {
                RuntimeIcon(
                    name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Edit,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            // 快捷删除按钮
            RuntimeIconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp),
            ) {
                RuntimeIcon(
                    name = top.wkbin.tianxuan.ui.components.RuntimeIconName.Trash,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}
