package top.wkbin.tianxuan.ui.settings

import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.ui.components.IconTile
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeSwitch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    RuntimeCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val rowModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        rowModifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(
            icon = icon,
            size = 30.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        value?.let {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            RuntimeIcon(RuntimeIconName.ChevronRight, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        trailing()
    }
}

@Composable
internal fun ToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(
            icon = icon,
            size = 30.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RuntimeSwitch(
            checked = checked,
            onCheckedChange = change,
            enabled = enabled,
        )
    }
}

@Composable
fun WebChatBridgeDialog(
    status: top.wkbin.tianxuan.runtime.webchat.WebChatServerStatus,
    toggling: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Globe, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("天玄智枢 Web 协作台")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "在同一 Wi-Fi / 局域网下，通过电脑浏览器连接天玄智枢，同步处理 Agent 任务、对话与 Linux 工作区文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "协作服务状态",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (toggling) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                RuntimeSwitch(
                                    checked = status.isRunning,
                                    onCheckedChange = onToggle,
                                    enabled = !toggling,
                                )
                            }
                        }

                        if (status.isRunning) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("浏览器访问地址", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = status.accessUrl,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    TextButton(
                                        onClick = {
                                            copyToClipboard(context, status.accessUrl, "天玄智枢协作地址", "已复制基础链接")
                                        }
                                    ) { Text("复制") }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("安全配对码 (PIN)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = status.pinCode,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    TextButton(
                                        onClick = {
                                            copyToClipboard(context, status.pinCode, "PIN", "已复制配对码")
                                        }
                                    ) { Text("复制") }
                                }
                            }

                            val directUrl = "${status.accessUrl}?token=${status.pinCode}"
                            Button(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        directUrl,
                                        "天玄智枢直连地址",
                                        "已复制免密直达链接，在电脑浏览器打开即可！",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp))
                                    Text("一键复制免密直达链接 (免输PIN)")
                                }
                            }

                            Text(
                                text = "💡 提示：确保电脑与手机处于同一 Wi-Fi；当前在线设备：${status.activeConnections} 台",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

/** 应用版本号；三处界面共用一份查询逻辑 */
@Composable
fun rememberAppVersion(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "unknown"
    }
}
