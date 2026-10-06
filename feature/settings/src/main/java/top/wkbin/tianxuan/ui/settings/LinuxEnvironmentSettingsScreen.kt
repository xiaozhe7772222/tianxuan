package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

/**
 * 二级子页 2：Linux 容器沙箱与存储
 */
@Composable
fun LinuxEnvironmentSettingsScreen(
    onBack: () -> Unit,
    onOpenDistroManagement: () -> Unit,
    onOpenStorageMounts: () -> Unit,
    onOpenStorageUsage: () -> Unit,
    onOpenAppManagement: () -> Unit,
    onOpenEnvironmentVariables: () -> Unit,
    onOpenSshSettings: () -> Unit,
    onOpenFtpSettings: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val executionMode by viewModel.executionMode.collectAsStateWithLifecycle()
    val effectiveExecutionMode by viewModel.effectiveExecutionMode.collectAsStateWithLifecycle()
    val privilegeState by viewModel.privilegeState.collectAsStateWithLifecycle()
    val switchingMode by viewModel.switchingMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val webChatStatus by viewModel.webChatStatus.collectAsStateWithLifecycle()

    var showExecutionModeDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showWebChatDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var privilegeResultMessage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    // Web 开关切换进行中指示，防止重复点击
    var webChatToggling by remember { mutableStateOf(false) }
    LaunchedEffect(webChatStatus.isRunning) { webChatToggling = false }

    if (showExecutionModeDialog) {
        ExecutionModeDialog(
            currentMode = executionMode,
            switching = switchingMode,
            onSelectMode = { mode ->
                showExecutionModeDialog = false
                viewModel.switchExecutionMode(mode) { success, msg ->
                    privilegeResultMessage = if (success) null else msg
                }
            },
            onDismiss = { showExecutionModeDialog = false },
        )
    }

    privilegeResultMessage?.let { errorMsg ->
        RuntimeAlertDialog(
            onDismissRequest = { privilegeResultMessage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                    Text("运行模式授权未通过")
                }
            },
            text = { Text(errorMsg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { privilegeResultMessage = null }) {
                    Text("知道了")
                }
            },
        )
    }

    if (showWebChatDialog) {
        WebChatBridgeDialog(
            status = webChatStatus,
            toggling = webChatToggling,
            onToggle = { enabled ->
                webChatToggling = true
                viewModel.toggleWebChatServer(enabled)
            },
            onDismiss = { showWebChatDialog = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Linux 容器与存储", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "容器系统与沙箱管理",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Server,
                        title = "Linux 发行版管理",
                        subtitle = "多沙箱并存 · 镜像拉取 · 一键切换主系统",
                        value = "${installedDistros.size} 套系统",
                        onClick = onOpenDistroManagement,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Storage,
                        title = "存储管理",
                        subtitle = "按 Linux、插件、项目与 Skills 分析占用",
                        onClick = onOpenStorageUsage,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.SdCard,
                        title = "存储挂载与共享",
                        subtitle = "PRoot 宿主存储映射 (-b /sdcard)",
                        onClick = onOpenStorageMounts,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "环境变量",
                        subtitle = "为终端、Agent 和工具注入用户变量",
                        onClick = onOpenEnvironmentVariables,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "SSH 远程访问",
                        subtitle = "公钥认证 · 端口与局域网监听 · 随运行时启动",
                        onClick = onOpenSshSettings,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.FolderOpen,
                        title = "FTP 远程文件访问",
                        subtitle = "挂载 Linux 根目录 (/) · FileZilla / 资源管理器直连",
                        onClick = onOpenFtpSettings,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Globe,
                        title = "天玄智枢 Web 协作台",
                        subtitle = if (webChatStatus.isRunning) {
                            "运行中 · ${webChatStatus.accessUrl} (PIN: ${webChatStatus.pinCode})"
                        } else {
                            "在同一 Wi-Fi 下使用电脑浏览器访问天玄 Agent 与工作区"
                        },
                        value = if (webChatStatus.isRunning) "已开启" else "未开启",
                        onClick = { showWebChatDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Package,
                        title = "应用管理",
                        subtitle = "同步系统/用户应用，查看禁用、冻结与后台联网限制状态",
                        onClick = onOpenAppManagement,
                    )
                }
            }

            item {
                Text(
                    text = "系统底层特权",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Key,
                        title = "系统运行特权模式",
                        subtitle = if (executionMode != effectiveExecutionMode) {
                            "首选 ${executionMode.shortLabel} 暂不可用：${privilegeState.reason}"
                        } else {
                            "PRoot 用户态沙箱 · Shizuku · Root"
                        },
                        value = if (executionMode == effectiveExecutionMode) {
                            effectiveExecutionMode.shortLabel
                        } else {
                            "${effectiveExecutionMode.shortLabel}（已降级）"
                        },
                        onClick = { showExecutionModeDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutionModeDialog(
    currentMode: ExecutionMode,
    switching: Boolean,
    onSelectMode: (ExecutionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = { if (!switching) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("选择系统运行模式", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "切换特权模式将自动发起授权检测；授权成功后即刻释放对应的高级系统与硬件能力。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (switching) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text("正在进行特权探测与授权申请…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ExecutionMode.entries.forEach { mode ->
                    ExecutionModeOptionItem(
                        mode = mode,
                        selected = currentMode == mode,
                        enabled = !switching,
                        onClick = { onSelectMode(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !switching) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ExecutionModeOptionItem(
    mode: ExecutionMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "当前激活",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = mode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "要求: ${mode.requiredPrivilege}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
