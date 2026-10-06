package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.feature.chat.R
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/**
 * 会话列表的两个分区：项目分组 + 最近。
 *
 * 与 [SessionDrawerItem] 一样，抽屉与常驻栏共用同一份实现——两者的差别只在容器。
 */

/** 分区标题行（名称 + 计数）。 */
@Composable
private fun SectionHeader(titleRes: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 空态占位。 */
@Composable
private fun EmptyHint(textRes: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(10.dp),
        )
    }
}

/** 项目分区。 */
@Composable
internal fun ProjectSection(
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
        SectionHeader(R.string.chat_drawer_projects, projectGroups.size)

        if (projectGroups.isEmpty()) {
            EmptyHint(R.string.chat_drawer_no_projects)
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
                    ProjectFolderRow(
                        group = group,
                        isExpanded = isExpanded,
                        onToggleExpand = { onToggleExpand(group.workspacePath) },
                        onCreate = { onCreateInProject(group) },
                    )

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

/** 项目文件夹行：展开箭头 + 项目名 + 会话数 + 快捷新建。 */
@Composable
private fun ProjectFolderRow(
    group: ProjectSessionGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RuntimeIcon(
            name = if (isExpanded) RuntimeIconName.FolderOpen else RuntimeIconName.Folder,
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

        RuntimeIconButton(onClick = onCreate, modifier = Modifier.size(24.dp)) {
            RuntimeIcon(
                name = RuntimeIconName.Plus,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        RuntimeIcon(
            name = if (isExpanded) RuntimeIconName.ChevronDown else RuntimeIconName.ChevronRight,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 最近分区（未关联项目的会话）。 */
@Composable
internal fun RecentSection(
    recentSessions: List<HarnessSessionEntity>,
    currentSessionId: String,
    sessionRunStates: Map<String, SessionRunState>,
    onSwitch: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(R.string.chat_drawer_recent, recentSessions.size)

        if (recentSessions.isEmpty()) {
            EmptyHint(R.string.chat_drawer_no_recent_sessions)
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