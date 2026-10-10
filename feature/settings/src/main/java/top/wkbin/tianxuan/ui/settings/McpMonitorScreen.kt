package top.wkbin.tianxuan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.core.model.McpConnectionState
import top.wkbin.tianxuan.harness.mcp.McpManager
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpMonitorScreen(
    onBack: () -> Unit,
    viewModel: McpMonitorViewModel = koinViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Scaffold(
        topBar = {
            RuntimeTopBar(
                title = "MCP 服务监控",
                statusText = null,
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "暂无 MCP 服务",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "在 MCP 服务设置中添加并启用服务后，这里会显示连接状态与工具数。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.serverId }) { info ->
                    McpMonitorCard(info)
                }
            }
        }
    }
}

@Composable
private fun McpMonitorCard(info: McpManager.McpMonitorInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    info.serverName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StateBadge(info.state, info.enabled)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "工具: ${info.toolCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!info.enabled) {
                    Text(
                        "已禁用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            info.lastError?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StateBadge(state: McpConnectionState, enabled: Boolean) {
    val (text, color) = when (state) {
        McpConnectionState.ONLINE -> "在线" to MaterialTheme.colorScheme.primary
        McpConnectionState.OFFLINE -> "离线" to MaterialTheme.colorScheme.error
        McpConnectionState.CHECKING -> "检测中…" to MaterialTheme.colorScheme.tertiary
        McpConnectionState.UNKNOWN -> if (enabled) "未知" to MaterialTheme.colorScheme.onSurfaceVariant else "已禁用" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Badge(containerColor = color) { Text(text) }
}
