package top.wkbin.tianxuan.ui.preview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.wkbin.tianxuan.ui.theme.AppleTypography
import top.wkbin.tianxuan.ui.components.CapsuleShape
import top.wkbin.tianxuan.ui.components.InfoRow
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeBadge
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCheckbox
import top.wkbin.tianxuan.ui.components.RuntimeDropdownMenu
import top.wkbin.tianxuan.ui.components.RuntimeDropdownMenuItem
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLiquidButton
import top.wkbin.tianxuan.ui.components.RuntimeModalBottomSheet
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedTextField
import top.wkbin.tianxuan.ui.components.RuntimeRadioButton
import top.wkbin.tianxuan.ui.components.RuntimeSearchBar
import top.wkbin.tianxuan.ui.components.RuntimeSegmentedControl
import top.wkbin.tianxuan.ui.components.RuntimeSheetHeader
import top.wkbin.tianxuan.ui.components.RuntimeSlider
import top.wkbin.tianxuan.ui.components.RuntimeSwitch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton
import top.wkbin.tianxuan.ui.components.SectionHeader
import top.wkbin.tianxuan.ui.components.StatusBadge
import top.wkbin.tianxuan.ui.components.TianXuanBrandBadge
import top.wkbin.tianxuan.ui.theme.ChengmingDarkColors
import top.wkbin.tianxuan.ui.theme.ChengmingLightColors
import top.wkbin.tianxuan.ui.theme.LiquidGlassTypography
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop
import top.wkbin.tianxuan.ui.theme.TianXuanShapes
import top.wkbin.tianxuan.ui.theme.ThemeStyle
import top.wkbin.tianxuan.ui.theme.XuantongDarkColors
import top.wkbin.tianxuan.ui.theme.XuantongLightColors

/**
 * 预览底衬壁纸类型定义。
 */
enum class CatalogWallpaper(val title: String) {
    AURORA("流光极光"),
    LIQUID_BLOOM("液态折射"),
    NEON_DARK("赛博暗夜"),
    MINIMAL_CLEAN("极简素白"),
}

/**
 * 天玄 · 澄明液态玻璃全景组件图鉴与工作台 (LiquidGlassCatalogScreen)。
 *
 * 聚合展示全套对齐 Apple HIG Materials 与 Kyant Backdrop 规范的液态玻璃控件：
 * 1. 字体排版标尺 (AppleTypography)；
 * 2. 交互式流光触感按钮 (RuntimeLiquidButton / RuntimeButton)；
 * 3. 药丸分段选择器 (RuntimeSegmentedControl)；
 * 4. 内嵌凹陷文本框与搜索栏 (RuntimeOutlinedTextField / RuntimeSearchBar)；
 * 5. 双重折射滑动条与开关 (RuntimeSlider / RuntimeSwitch)；
 * 6. 模态底部抽屉与多层嵌套玻璃 (RuntimeModalBottomSheet Glass-on-Glass)；
 * 7. 上下文浮动菜单与弹窗 (RuntimeDropdownMenu / RuntimeAlertDialog)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassCatalogScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var currentThemeStyle by remember { mutableStateOf(ThemeStyle.LIQUID_GLASS) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var selectedWallpaper by remember { mutableStateOf(CatalogWallpaper.AURORA) }

    // 状态模拟
    var segmentedIndex by remember { mutableIntStateOf(0) }
    var sampleText by remember { mutableStateOf("Linux AIRuntime (天玄)") }
    var searchQuery by remember { mutableStateOf("") }
    var switchState by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(0.65f) }
    var checkboxState by remember { mutableStateOf(true) }
    var radioIndex by remember { mutableIntStateOf(0) }

    // 弹窗与抽屉控制
    val sheetScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var showDropdownMenu by remember { mutableStateOf(false) }

    val colorScheme = when (currentThemeStyle) {
        ThemeStyle.LIQUID_GLASS -> if (isDarkTheme) ChengmingDarkColors else ChengmingLightColors
        ThemeStyle.MATERIAL_YOU -> if (isDarkTheme) XuantongDarkColors else XuantongLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiquidGlassTypography,
        shapes = TianXuanShapes,
    ) {
        val rootBackdrop = rememberLayerBackdrop()

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF090B10) else Color(0xFFF2F2F7)),
        ) {
            // 1. 底层高保真流光/折射壁纸渲染（受 rootBackdrop 捕获供全屏玻璃控件折射）
            CatalogWallpaperCanvas(
                wallpaper = selectedWallpaper,
                isDark = isDarkTheme,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) {
                            Modifier.layerBackdrop(rootBackdrop)
                        } else Modifier,
                    ),
            )

            // 2. 顶层主内容，向内注入 LocalLiquidGlassSurfaceBackdrop
            CompositionLocalProvider(
                LocalLiquidGlassSurfaceBackdrop provides if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) rootBackdrop else null,
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        CatalogTopBar(
                            themeStyle = currentThemeStyle,
                            isDark = isDarkTheme,
                            wallpaper = selectedWallpaper,
                            onToggleTheme = {
                                currentThemeStyle = if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) {
                                    ThemeStyle.MATERIAL_YOU
                                } else {
                                    ThemeStyle.LIQUID_GLASS
                                }
                            },
                            onToggleDark = { isDarkTheme = !isDarkTheme },
                            onSelectWallpaper = { selectedWallpaper = it },
                            onBack = onBack,
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // 顶部状态横幅
                        CatalogModeBanner(currentThemeStyle, isDarkTheme, selectedWallpaper)

                        // 展区 1：字体与排版层级 (Typography Scale)
                        CatalogTypographySection()

                        // 展区 2：按钮与触控物理 (Buttons & Interactive Physics)
                        CatalogButtonsSection()

                        // 展区 3：胶囊分段器 (Segmented Controls)
                        CatalogSegmentedSection(
                            selectedIndex = segmentedIndex,
                            onSelect = { segmentedIndex = it },
                        )

                        // 展区 4：文本输入框与搜索栏 (TextFields & Search)
                        CatalogInputSection(
                            sampleText = sampleText,
                            onTextChange = { sampleText = it },
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )

                        // 展区 5：开关与滑动条 (Toggles & Sliders)
                        CatalogControlsSection(
                            switchState = switchState,
                            onSwitchChange = { switchState = it },
                            sliderValue = sliderValue,
                            onSliderChange = { sliderValue = it },
                            checkboxState = checkboxState,
                            onCheckboxChange = { checkboxState = it },
                            radioIndex = radioIndex,
                            onRadioChange = { radioIndex = it },
                        )

                        // 展区 6：浮层、抽屉与多层嵌套 (Sheets & Dialogs Glass-on-Glass)
                        CatalogOverlaysSection(
                            onOpenSheet = { showBottomSheet = true },
                            onOpenDialog = { showAlertDialog = true },
                            onOpenDropdown = { showDropdownMenu = true },
                            dropdownExpanded = showDropdownMenu,
                            onDismissDropdown = { showDropdownMenu = false },
                        )

                        // 展区 7：内容卡片与状态角标 (Cards & Badges)
                        CatalogCardsSection()

                        Spacer(Modifier.height(48.dp))
                    }
                }

                // 模态底部抽屉 (Modal Bottom Sheet) 实装演示
                if (showBottomSheet) {
                    RuntimeModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = bottomSheetState,
                    ) {
                        RuntimeSheetHeader(
                            title = "液态玻璃抽屉 (Sheet Presentation)",
                            onDismiss = { showBottomSheet = false },
                            onConfirm = { showBottomSheet = false },
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "✨ Glass-on-Glass 安全嵌套演示：本抽屉使用 Thick 材质，抽屉内部的卡片与按钮已自动通过 CompositionLocalProvider 折射本抽屉，彻底杜绝 RenderThread 递归合成崩溃。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // 抽屉内部嵌套卡片
                            RuntimeCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "嵌套卡片 (Second Glass Layer)",
                                        style = AppleTypography.Headline,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "注意观察此卡片边缘的透镜折射与高光，背景正确折射抽屉本身，呈现物理级半透明进深感。",
                                        style = AppleTypography.Subhead,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    RuntimeLiquidButton(
                                        onClick = { showBottomSheet = false },
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("确认并关闭抽屉", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // 对话框 (AlertDialog) 演示
                if (showAlertDialog) {
                    RuntimeAlertDialog(
                        onDismissRequest = { showAlertDialog = false },
                        title = { Text("天玄液态玻璃对话框", style = AppleTypography.Headline) },
                        text = {
                            Text(
                                "完全基于 Apple HIG Materials 与 Kyant Backdrop 架构打造，支持景深遮罩与按压触感反馈。",
                                style = AppleTypography.Body,
                            )
                        },
                        confirmButton = {
                            RuntimeLiquidButton(
                                onClick = { showAlertDialog = false },
                                tint = MaterialTheme.colorScheme.primary,
                            ) {
                                Text("知道了", color = Color.White)
                            }
                        },
                        dismissButton = {
                            RuntimeTextButton(onClick = { showAlertDialog = false }) {
                                Text("取消")
                            }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 各展区精装子组件实现
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogModeBanner(
    themeStyle: ThemeStyle,
    isDark: Boolean,
    wallpaper: CatalogWallpaper,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TianXuanBrandBadge(size = 42.dp)
                Column {
                    Text(
                        text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "澄明 · 液态玻璃模式" else "玄同 · Material You 模式",
                        style = AppleTypography.Headline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "底衬: ${wallpaper.title} · ${if (isDark) "深色" else "浅色"}外观",
                        style = AppleTypography.Footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RuntimeBadge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(if (themeStyle == ThemeStyle.LIQUID_GLASS) "iOS HIG" else "M3 Native")
            }
        }
    }
}

@Composable
private fun CatalogTypographySection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "字体与排版标尺 (Apple HIG Scale)",
                subtitle = "San Francisco (SF Pro) 字重阶梯与负向 Tight Tracking",
            )
            Text("LargeTitle (34sp Bold)", style = AppleTypography.LargeTitle)
            Text("Title1 (28sp Bold)", style = AppleTypography.Title1)
            Text("Title2 (22sp Bold)", style = AppleTypography.Title2)
            Text("Title3 (20sp SemiBold)", style = AppleTypography.Title3)
            Text("Headline (17sp SemiBold)", style = AppleTypography.Headline)
            Text("Body (17sp Regular) · 天玄移动端原生 Linux 沙箱运行环境", style = AppleTypography.Body)
            Text("Callout (16sp Regular) · 智枢核心与终端原生 PTY 结对通信", style = AppleTypography.Callout)
            Text("Footnote (13sp Regular) · Apple HIG Materials 光学材质分级对齐", style = AppleTypography.Footnote)
            Text("Caption (12sp Regular) · 1px 镜面天顶光反光线", style = AppleTypography.Caption1)
        }
    }
}

@Composable
private fun CatalogButtonsSection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "按钮与触控流光 (Liquid Buttons)",
                subtitle = "AGSL 触控流动漫射光斑 + tanh 双轴阻尼拉伸形变",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 原版 Kyant 胶囊流体按钮
                RuntimeLiquidButton(
                    onClick = {},
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp), tint = Color.White)
                    Text("流光按钮", color = Color.White, style = AppleTypography.Button)
                }

                // 成功/绿色浸润按钮
                RuntimeLiquidButton(
                    onClick = {},
                    tint = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(18.dp), tint = Color.White)
                    Text("成功浸润", color = Color.White, style = AppleTypography.Button)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 标准 RuntimeButton
                RuntimeButton(
                    onClick = {},
                    tonal = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tonal 填充")
                }

                // 边框按钮与图标按钮
                RuntimeOutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Text("边框按钮")
                }

                RuntimeIconButton(
                    onClick = {},
                ) {
                    RuntimeIcon(RuntimeIconName.Refresh)
                }
                RuntimeIconButton(
                    onClick = {},
                ) {
                    RuntimeIcon(RuntimeIconName.Settings)
                }
            }
        }
    }
}

@Composable
private fun CatalogSegmentedSection(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = "胶囊分段选择器 (Segmented Control)",
                subtitle = "UltraThin 底轨 + Thin 色散滑块 + 速度微拉伸弹性手感",
            )

            RuntimeSegmentedControl(
                options = listOf("全局概览", "终端中枢", "智坊", "摇光"),
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "当前激活项: 第 ${selectedIndex + 1} 项 · 点击或左右切换可感知 Apple 规范的 LiquidSpringSpec 动力学与震动反馈。",
                style = AppleTypography.Footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CatalogInputSection(
    sampleText: String,
    onTextChange: (String) -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "输入与搜索 (Inset Glass Fields)",
                subtitle = "UltraThin 凹陷微内阴影 + 1px 镜面反光线 + 获焦流光外发光",
            )

            // 全圆角搜索栏
            RuntimeSearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = "搜索沙箱应用、命令或工作区...",
                modifier = Modifier.fillMaxWidth(),
            )

            // 带标签与图标的内嵌输入框
            RuntimeOutlinedTextField(
                value = sampleText,
                onValueChange = onTextChange,
                label = { Text("沙箱容器名称") },
                placeholder = { Text("请输入容器名称...") },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp)) },
                supportingText = { Text("支持中英文、数字与横线组合") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CatalogControlsSection(
    switchState: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    checkboxState: Boolean,
    onCheckboxChange: (Boolean) -> Unit,
    radioIndex: Int,
    onRadioChange: (Int) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "开关与滑动条 (Toggles & Sliders)",
                subtitle = "双重折射拇指 + 阻尼挤压拉伸 (Squash & Stretch)",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("液态玻璃开关 (LiquidToggle)", style = AppleTypography.Headline)
                    Text("双重折射色散滑块", style = AppleTypography.Footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RuntimeSwitch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("阻尼滑动条 (LiquidSlider)", style = AppleTypography.Callout)
                    Text("${(sliderValue * 100).toInt()}%", style = AppleTypography.Callout, fontWeight = FontWeight.Bold)
                }
                RuntimeSlider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuntimeCheckbox(checked = checkboxState, onCheckedChange = onCheckboxChange)
                    Spacer(Modifier.width(8.dp))
                    Text("跟随系统深浅色", style = AppleTypography.Subhead)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuntimeRadioButton(selected = radioIndex == 0, onClick = { onRadioChange(0) })
                    Text("选项 A", style = AppleTypography.Subhead)
                    Spacer(Modifier.width(8.dp))
                    RuntimeRadioButton(selected = radioIndex == 1, onClick = { onRadioChange(1) })
                    Text("选项 B", style = AppleTypography.Subhead)
                }
            }
        }
    }
}

@Composable
private fun CatalogOverlaysSection(
    onOpenSheet: () -> Unit,
    onOpenDialog: () -> Unit,
    onOpenDropdown: () -> Unit,
    dropdownExpanded: Boolean,
    onDismissDropdown: () -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "抽屉、弹窗与浮层 (Sheets & Overlays)",
                subtitle = "Thick/Regular 材质 + 28dp 大圆角 + Glass-on-Glass 安全嵌套",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeButton(
                    onClick = onOpenSheet,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开底部抽屉")
                }

                RuntimeOutlinedButton(
                    onClick = onOpenDialog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("弹出对话框")
                }

                Box {
                    RuntimeIconButton(
                        onClick = onOpenDropdown,
                    ) {
                        RuntimeIcon(RuntimeIconName.More)
                    }
                    RuntimeDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = onDismissDropdown,
                    ) {
                        RuntimeDropdownMenuItem(
                            text = { Text("工作区设置") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Settings, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("重载沙箱") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("清除缓存") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCardsSection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "内容容器与状态角标 (Cards & Badges)",
                subtitle = "Thick 材质内容面板 + 色散水滴状态角标",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(text = "运行中", color = Color(0xFF34C759))
                StatusBadge(text = "已就绪", color = MaterialTheme.colorScheme.primary)
                StatusBadge(text = "异常", color = MaterialTheme.colorScheme.error)
                RuntimeBadge(containerColor = Color(0xFFFF9500)) {
                    Text("NEW")
                }
                RuntimeBadge(containerColor = Color(0xFFFF2D55)) {
                    Text("99+")
                }
            }

            InfoRow(
                label = "ARM64 架构沙箱",
                value = "Ubuntu 24.04 LTS (PRoot)",
            )
            InfoRow(
                label = "渲染着色器核心",
                value = "Android AGSL Liquid Shaders",
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 顶栏与动态壁纸画布
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogTopBar(
    themeStyle: ThemeStyle,
    isDark: Boolean,
    wallpaper: CatalogWallpaper,
    onToggleTheme: () -> Unit,
    onToggleDark: () -> Unit,
    onSelectWallpaper: (CatalogWallpaper) -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                RuntimeIconButton(onClick = onBack) {
                    RuntimeIcon(RuntimeIconName.Back)
                }
            }
            Column {
                Text(
                    text = "液态玻璃全景图鉴",
                    style = AppleTypography.Headline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "TianXuan Liquid Glass Catalog",
                    style = AppleTypography.Caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 壁纸切换器
            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .clickable {
                        val nextIdx = (wallpaper.ordinal + 1) % CatalogWallpaper.entries.size
                        onSelectWallpaper(CatalogWallpaper.entries[nextIdx])
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "壁纸: ${wallpaper.title}",
                    style = AppleTypography.Caption1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 主题切换（澄明 vs 玄同）
            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(
                        if (themeStyle == ThemeStyle.LIQUID_GLASS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    )
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "澄明" else "玄同",
                    style = AppleTypography.Caption1,
                    fontWeight = FontWeight.Bold,
                    color = if (themeStyle == ThemeStyle.LIQUID_GLASS) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }

            // 深浅切换
            RuntimeIconButton(
                onClick = onToggleDark,
            ) {
                RuntimeIcon(if (isDark) RuntimeIconName.Palette else RuntimeIconName.Sparkles)
            }
        }
    }
}

/**
 * 动态流光壁纸画布：生成丰富渐变色块，测试玻璃的折射效果。
 */
@Composable
private fun CatalogWallpaperCanvas(
    wallpaper: CatalogWallpaper,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    // aurora 渐变球的圆心偏移动画（已被移除：原 animOffset 从未被 Canvas 读取，
    // 却以 60fps 持续驱动重组与重绘，浪费 CPU/电量）
    val auroraCenter = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)

    when (wallpaper) {
        CatalogWallpaper.AURORA -> {
            Canvas(modifier = modifier) {
                // 极光渐变球
                val w = size.width
                val h = size.height

                drawRect(if (isDark) Color(0xFF090D18) else Color(0xFFE8EEF8))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF007AFF).copy(alpha = 0.45f) else Color(0xFF007AFF).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.25f, h * 0.20f),
                        radius = w * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFFAF52DE).copy(alpha = 0.40f) else Color(0xFFAF52DE).copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.80f, h * 0.45f),
                        radius = w * 0.70f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF34C759).copy(alpha = 0.30f) else Color(0xFF34C759).copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.30f, h * 0.75f),
                        radius = w * 0.60f,
                    ),
                )
            }
        }
        CatalogWallpaper.LIQUID_BLOOM -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height

                drawRect(if (isDark) Color(0xFF100A18) else Color(0xFFFAF0F5))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2D55).copy(alpha = if (isDark) 0.40f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.70f, h * 0.25f),
                        radius = w * 0.60f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF9500).copy(alpha = if (isDark) 0.35f else 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.20f, h * 0.55f),
                        radius = w * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5856D6).copy(alpha = if (isDark) 0.40f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.60f, h * 0.85f),
                        radius = w * 0.70f,
                    ),
                )
            }
        }
        CatalogWallpaper.NEON_DARK -> {
            Canvas(modifier = modifier) {
                drawRect(Color(0xFF040608))
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.30f),
                        radius = w * 0.50f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF007F).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.70f),
                        radius = w * 0.55f,
                    ),
                )
            }
        }
        CatalogWallpaper.MINIMAL_CLEAN -> {
            Canvas(modifier = modifier) {
                drawRect(if (isDark) Color(0xFF141416) else Color(0xFFF7F7F8))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Compose @Preview 支持（Android Studio 实时可视化渲染）
// ═══════════════════════════════════════════════════════════════════════════

@Preview(name = "澄明 · 液态玻璃 (深色)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogDark() {
    LiquidGlassCatalogScreen()
}

@Preview(name = "澄明 · 液态玻璃 (浅色)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogLight() {
    // 浅色预览
    MaterialTheme(colorScheme = ChengmingLightColors, typography = LiquidGlassTypography) {
        LiquidGlassCatalogScreen()
    }
}
