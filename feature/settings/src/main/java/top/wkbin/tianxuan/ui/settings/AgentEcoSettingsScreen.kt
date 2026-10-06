package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

/**
 * 二级子页 1：智能体与 AI 模型生态
 */
@Composable
fun AgentEcoSettingsScreen(
    onBack: () -> Unit,
    onOpenModelProfiles: () -> Unit,
    onOpenLocalLlm: () -> Unit,
    onOpenToolCenter: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenSubagentSettings: () -> Unit,
    onOpenSkillSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenQuickPhrases: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCcSwitch: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val phrases by viewModel.quickPhrases.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("智能体与模型", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "模型档案与提供商",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Model,
                        title = "模型档案管理",
                        subtitle = "配置 OpenAI / DeepSeek / Claude / 本地大模型密钥与端点",
                        value = if (models.isEmpty()) "未配置" else "${models.size} 个模型",
                        onClick = onOpenModelProfiles,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Cpu,
                        title = "本地 LLM",
                        subtitle = "导入或下载 GGUF，在 ARM64 设备端通过 llama.cpp 离线推理",
                        onClick = onOpenLocalLlm,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Chat,
                        title = "快捷短语与常用指令",
                        subtitle = "自定义智枢空白页快捷开始卡片与高频提示词模板",
                        value = "${phrases.count { it.isEnabled }} 条已启用",
                        onClick = onOpenQuickPhrases,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "数据统计与用量分析",
                        subtitle = "Token 消耗、活跃度热力图、模型与话题排行",
                        onClick = onOpenStats,
                    )
                }
            }

            item {
                Text(
                    text = "工具与插件生态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Wrench,
                        title = "插件与底层工具中心",
                        subtitle = "安装 llama.cpp、QEMU 与底层开发环境",
                        onClick = onOpenToolCenter,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "智能体中枢 (CC-Switch)",
                        subtitle = "统一管理 Claude Code、OpenClaw、Hermes 版本与模型多源热切",
                        onClick = onOpenCcSwitch,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Agent 执行与上下文", subtitle = "思考流、上下文压缩、工具调用限制与系统提示词", onClick = onOpenAgentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "子智能体角色", subtitle = "自动委派策略与可用的子智能体角色", onClick = onOpenSubagentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Sparkles, title = "Skills 与插件", subtitle = "管理 Skill 提示词、脚本包与运行时插件", value = "${skills.count { it.isEnabled }} 个技能", onClick = onOpenSkillSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "MCP 协议生态与服务",
                        subtitle = "管理 SQLite、Git、Fetch 等 Model Context Protocol 协议服务",
                        onClick = onOpenMcpSettings,
                    )
                }
            }
        }
    }
}
