package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.IconTile
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.RuntimeBottomBar
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.liquidGlassContent
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassBackdrop

/**
 * 天玄 · 摇光配置 (TianXuan Settings & Models)
 */
@Composable
fun SettingsScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenAgentEco: () -> Unit,
    onOpenLinuxEnv: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSystemDev: () -> Unit,
    onOpenAboutCommunity: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenA2uiPoc: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val effectiveExecutionMode by viewModel.effectiveExecutionMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()

    val themeLabel = when (themeMode) {
        "light" -> "浅色"
        "dark" -> "曜石"
        else -> "跟随系统"
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersionName = rememberAppVersion()

    val glassBackdrop = LocalLiquidGlassBackdrop.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "天玄 · 摇光",
                statusText = "系统设置与控制中枢",
            )
        },
        bottomBar = {
            if (glassBackdrop == null) {
                RuntimeBottomBar(MainDestination.Settings, onNavigate)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassContent()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 全局功能搜索快捷入口卡片
            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSearch,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Search,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "搜索全局功能与设置选项...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeIcon(
                            name = RuntimeIconName.ChevronRight,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "系统与配置分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                )
            }

            // 1. 智能体与 AI 模型生态
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Brain,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "智能体与 AI 模型",
                    subtitle = "模型档案 · 插件工具中心 · 技能与 MCP 生态",
                    badge = if (models.isEmpty()) "未配置模型" else "${models.size} 个模型 · ${skills.count { it.isEnabled }} 技能",
                    onClick = onOpenAgentEco,
                )
            }

            // 2. Linux 容器沙箱与存储
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Server,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "Linux 容器与存储",
                    subtitle = "多发行版管理 · 宿主存储映射 · 运行特权模式",
                    badge = "${installedDistros.size} 套系统 · ${effectiveExecutionMode.shortLabel}",
                    onClick = onOpenLinuxEnv,
                )
            }

            // 3. 外观、字号与终端定制
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Palette,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "外观、字号与终端定制",
                    subtitle = "深浅色主题 · 应用字号缩放 · 终端配色与字体",
                    badge = "$themeLabel · ${terminalFontSize}sp",
                    onClick = onOpenAppearance,
                )
            }

            // 4. 系统保活与开发者诊断
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Admin,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "系统保活与开发者诊断",
                    subtitle = "后台电池优化白名单 · 调试监控 · PRoot 控制台",
                    badge = if (developer) "诊断模式已开启" else "运行平稳",
                    onClick = onOpenSystemDev,
                )
            }

            // 4.5 开发者实验功能：A2UI 渲染器 PoC（仅诊断模式可见）
            if (developer) {
                item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Sparkles,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "A2UI 渲染器 PoC",
                    subtitle = "智能体把回答画成原生界面卡片 · 点开可体验示例",
                    badge = "实验功能",
                    onClick = onOpenA2uiPoc,
                )
            }
            }

            // 4.6 RAG 知识库：对全部用户可见（非诊断功能）
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Document,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "RAG 知识库",
                    subtitle = "向量化检索增强 · 自动注入对话上下文 · 支持 @kb:文档名 手动引用",
                    badge = "AI 增强",
                    onClick = onOpenKnowledge,
                )
            }

            // 5. 关于、更新与官方社区
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Community,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "关于、更新与官方社区",
                    subtitle = "检查新版本 · GitHub 开源仓库 · 官方 QQ 交流群",
                    badge = if (appVersionName == "unknown") "版本号未知 · 稳定版" else "v$appVersionName 稳定版",
                    onClick = onOpenAboutCommunity,
                )
            }
        }
    }
}

/**
 * 现代高质感大类导航卡片（紧凑精致）
 */
@Composable
private fun SettingsCategoryCard(
    icon: RuntimeIconName,
    accent: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(
                icon = icon,
                size = 36.dp,
                color = accent,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                    )
                }
            }

            RuntimeIcon(
                name = RuntimeIconName.ChevronRight,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
