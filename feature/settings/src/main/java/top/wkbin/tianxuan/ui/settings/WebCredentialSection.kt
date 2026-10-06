package top.wkbin.tianxuan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.ui.components.RuntimeTextButton

/**
 * Web 控制台凭据区。
 *
 * 从 CcSwitchScreen 抽出：这块的逻辑（弱口令判定、复制反馈）自成一体，
 * 而 CcSwitchScreen 已远超行数棘轮，不能再往上加。
 *
 * 口径：口令为空时显示「未生成」而不是空白。用户看到空白会以为服务坏了，
 * 而真实原因是凭据文件还没落盘。
 */
@Composable
internal fun WebCredentialSection(
    username: String,
    password: String,
    onRotatePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Web 控制台访问凭据 (HTTP Basic 认证)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RuntimeTextButton(
                onClick = {
                    val creds = "$username / $password"
                    copyToClipboard(context, creds, "CC-Switch 凭据", "已复制控制台账号密码: $creds")
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("一键复制", style = MaterialTheme.typography.labelSmall)
            }
            RuntimeTextButton(
                // 过去这个按钮把口令直接打回 admin123，等于留了一条一键降级到
                // 弱口令的后门。改为生成强随机口令。
                onClick = onRotatePassword,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text("生成强口令", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    // 存量弱口令必须显式提示，但不能自动改——自动改会让用户当场失去访问权
    // （他记的是旧口令，且别处可能已保存）。只能告知，由他决定。
    if (CcSwitchCredentials.isKnownWeakPassword(password)) {
        Text(
            text = if (password.isBlank()) {
                "尚未生成访问口令，点「生成强口令」创建。该中枢在同一局域网内可达，" +
                    "控制台里存放着模型 API Key，请勿使用简单口令。"
            } else {
                "当前使用的是历史默认口令，同一局域网内的设备均可登录。" +
                    "控制台里存放着模型 API Key，请点「生成强口令」更换。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "账号：$username",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "密码：${password.ifBlank { "未生成" }}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = if (password.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}