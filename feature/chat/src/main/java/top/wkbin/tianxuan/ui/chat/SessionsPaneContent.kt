package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.feature.chat.R
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeFilledTonalButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/** 常驻会话栏的测试标签，供渲染测试断言。 */
const val SESSIONS_PANE_TEST_TAG: String = "tianxuan-sessions-pane"

/** 常驻栏顶部「收起会话栏」按钮的测试标签。 */
const val SESSIONS_PANE_COLLAPSE_TEST_TAG: String = "tianxuan-sessions-pane-collapse"

/**
 * 会话栏内容区：新建会话 + 快捷入口 + 项目/最近分区。
 *
 * 抽屉（[SessionsSideDrawer]）与平板常驻栏（[SessionsPersistentPane]）共用这一份内容。
 * 抽出它的意义不是省代码，而是**保证两种形态看到的会话列表永远一致**——否则修一处 bug
 * 要记得改两处，早晚漏掉一处，用户在平板上看到的行为就与手机上不同。
 *
 * @param collapseSlot 顶部右侧的槽位。抽屉传「关闭按钮」，常驻栏传「收起按钮」，为 null 则不渲染。
 * @param afterAction 所有跳转类操作（切换会话、新建、打开扩展）执行后的回调。
 *        抽屉需要它来关闭弹窗；常驻栏不需要，传空实现。
 */
@Composable
internal fun SessionsPaneContent(
    sessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    workspaces: List<WorkspaceProject>,
    sessionRunStates: Map<String, SessionRunState>,
    collapseSlot: (@Composable () -> Unit)?,
    onSwitch: (String) -> Unit,
    onNew: () -> Unit,
    onCreateInWorkspace: (WorkspaceProject) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onOpenSkills: (() -> Unit)?,
    onOpenRuntime: (() -> Unit)?,
    afterAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 重命名与删除二次确认状态：两个形态都需要，故收在内容区内而非各写一份
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    val partition = remember(sessions, workspaces) { partitionSessions(sessions, workspaces) }
    var expandedProjects by rememberSaveable {
        mutableStateOf(
            partition.projectGroups
                .filter { group -> group.sessions.isNotEmpty() }
                .map { it.workspacePath }
                .toSet(),
        )
    }

    Column(modifier = modifier) {
        SessionsPaneHeader(
            totalSessionsCount = sessions.size,
            collapseSlot = collapseSlot,
        )

        NewSessionButton(onClick = { afterAction(); onNew() })

        if (onOpenSkills != null || onOpenRuntime != null) {
            QuickEntryRow(
                onOpenSkills = onOpenSkills,
                onOpenRuntime = onOpenRuntime,
                afterAction = afterAction,
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                onSwitch = { id -> afterAction(); onSwitch(id) },
                onCreateInProject = { group ->
                    val matched = workspaces.firstOrNull { it.linuxPath == group.workspacePath }
                        ?: WorkspaceProject(
                            name = group.projectName,
                            path = group.workspacePath,
                            linuxPath = group.workspacePath,
                            sizeBytes = 0L,
                            projectType = group.projectType,
                        )
                    afterAction()
                    onCreateInWorkspace(matched)
                },
                onRename = { id -> renameTargetId = id },
                onDelete = { id -> deleteTargetId = id },
            )

            RecentSection(
                recentSessions = partition.recentSessions,
                currentSessionId = currentSessionId,
                sessionRunStates = sessionRunStates,
                onSwitch = { id -> afterAction(); onSwitch(id) },
                onRename = { id -> renameTargetId = id },
                onDelete = { id -> deleteTargetId = id },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 重命名与删除二次确认：放在内容区末尾而非调用方，两个形态行为一致
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

    deleteTargetId?.let { targetId ->
        SessionDeleteDialog(
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                deleteTargetId = null
                onDelete(targetId)
            },
        )
    }
}

/** 顶部标题行：品牌 + 标题 + 会话计数 + 可选收起槽位。 */
@Composable
private fun SessionsPaneHeader(
    totalSessionsCount: Int,
    collapseSlot: (@Composable () -> Unit)?,
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
            modifier = Modifier.weight(1f, fill = false),
        ) {
            top.wkbin.tianxuan.ui.components.TianXuanBrandBadge(size = 24.dp)
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        collapseSlot?.invoke()
    }
}

/** 「新任务 / 新建会话」大按钮。 */
@Composable
private fun NewSessionButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        RuntimeButton(
            onClick = onClick,
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
                    name = RuntimeIconName.Edit,
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
}

/** 快捷扩展与监控入口。 */
@Composable
private fun QuickEntryRow(
    onOpenSkills: (() -> Unit)?,
    onOpenRuntime: (() -> Unit)?,
    afterAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onOpenSkills != null) {
            QuickEntryButton(
                icon = RuntimeIconName.Extension,
                labelRes = R.string.chat_drawer_quick_skills,
                onClick = { afterAction(); onOpenSkills() },
                modifier = Modifier.weight(1f),
            )
        }
        if (onOpenRuntime != null) {
            QuickEntryButton(
                icon = RuntimeIconName.Logs,
                labelRes = R.string.chat_drawer_quick_runtime,
                onClick = { afterAction(); onOpenRuntime() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 单个快捷入口按钮。 */
@Composable
private fun QuickEntryButton(
    icon: RuntimeIconName,
    labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeFilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RuntimeIcon(
                name = icon,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 删除会话二次确认。 */
@Composable
private fun SessionDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    top.wkbin.tianxuan.ui.components.RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_delete_session_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                stringResource(R.string.chat_delete_session_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            RuntimeButton(onClick = onConfirm) {
                Text(stringResource(R.string.chat_confirm_delete), color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            top.wkbin.tianxuan.ui.components.RuntimeTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_cancel))
            }
        },
    )
}