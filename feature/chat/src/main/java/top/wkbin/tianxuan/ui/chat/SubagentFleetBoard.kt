package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.harness.session.ConversationBranch

/**
 * 子代理并行看板：把同一会话内的全部 subagent 泳道按**实时状态**可视化，
 * 让用户一眼看清「哪些在跑 / 哪些异常 / 各自步数」，点击某行即展开该泳道结果。
 *
 * 数据来自 [ConversationBranch]（kind = SUBAGENT）：isBusy/isFaulted/toolCallCount
 * 已由 LaneManager 投影，无需新增后端字段。
 */
@Composable
internal fun SubagentFleetBoard(
    subagents: List<ConversationBranch>,
    onOpen: (ConversationBranch) -> Unit,
) {
    val running = subagents.count { it.isBusy }
    val faulted = subagents.count { it.faulted && !it.isBusy }
    val idle = subagents.size - running - faulted

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("子代理并行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            FleetStat("运行 $running", MaterialTheme.colorScheme.primary)
            FleetStat("异常 $faulted", MaterialTheme.colorScheme.error)
            FleetStat("其他 $idle", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        subagents.forEach { branch ->
            val (label, color) = when {
                branch.isBusy -> "运行中" to MaterialTheme.colorScheme.primary
                branch.faulted -> "异常" to MaterialTheme.colorScheme.error
                else -> "空闲" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    // 只有能定位到 durable lane 时才能打开只读 transcript
                    .clickable(enabled = branch.laneName != null) { onOpen(branch) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (branch.toolCallCount > 0) {
                    Text(
                        "${branch.toolCallCount} 步",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun FleetStat(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 6.dp),
    )
}
