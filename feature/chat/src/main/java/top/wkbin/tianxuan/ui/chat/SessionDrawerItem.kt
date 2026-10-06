package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import top.wkbin.tianxuan.feature.chat.R
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 运行状态对应的圆点颜色：待审批用紫色以便与「运行中」的橙色一眼区分。 */
private fun runStateDotColor(runState: SessionRunState): Color = when (runState) {
    SessionRunState.RUNNING -> Color(0xFFF59E0B)
    SessionRunState.WAITING_APPROVAL -> Color(0xFF8B5CF6)
    SessionRunState.FAILED -> Color(0xFFEF4444)
    SessionRunState.COMPLETED -> Color(0xFF10B981)
    SessionRunState.IDLE -> Color(0xFF10B981)
}

/**
 * 会话时间的人性化标签。
 *
 * 用相对时间（刚刚 / N 分钟前 / 昨天）而不是绝对时刻：会话列表的判断依据是「最近动过没有」，
 * 绝对时间戳要读者自己做减法。
 */
@Composable
internal fun formatSessionTime(timestamp: Long): String {
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
 * 单条会话项。
 *
 * 抽屉与常驻栏共用：两者的差别只有外层容器（Dialog / 侧栏），条目本身完全一致。
 */
@Composable
internal fun SessionDrawerItem(
    session: HarnessSessionEntity,
    isCurrent: Boolean,
    runState: SessionRunState,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dotColor = runStateDotColor(runState)
    val timeLabel = formatSessionTime(session.updatedAt)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        border = if (isCurrent) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
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
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
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

            RuntimeIconButton(onClick = onRename, modifier = Modifier.size(24.dp)) {
                RuntimeIcon(
                    name = RuntimeIconName.Edit,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            RuntimeIconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                RuntimeIcon(
                    name = RuntimeIconName.Trash,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}