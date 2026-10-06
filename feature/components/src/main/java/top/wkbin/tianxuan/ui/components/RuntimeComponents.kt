package top.wkbin.tianxuan.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.drawscope.scale
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.wkbin.tianxuan.feature.components.R
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassBackdrop
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop

/**
 * 天玄 (TianXuan) 四大核心中枢导航定义：
 * 天玄（开辟画布）· 智枢（AI 结对）· 智坊（工作区）· 摇光（设置与模型）
 */
enum class MainDestination(val labelRes: Int, val subtitleRes: Int, val icon: RuntimeIconName) {
    Home(R.string.components_nav_home, R.string.components_nav_subtitle_home, RuntimeIconName.NavDashboard),
    Agent(R.string.components_nav_agent, R.string.components_nav_subtitle_agent, RuntimeIconName.NavMessage),
    Workspace(R.string.components_nav_workspace, R.string.components_nav_subtitle_workspace, RuntimeIconName.NavRepository),
    Settings(R.string.components_nav_settings, R.string.components_nav_subtitle_settings, RuntimeIconName.NavSettings),
}

/**
 * 天玄 · 核心底部中枢导航栏。
 * 默认（玄同主题）为 Material 3 Native NavigationBar；澄明（液态玻璃）主题下渲染为
 * 悬浮磨砂玻璃胶囊：半透明毛玻璃折射底层流光 + 圆角药丸 + 玻璃质感选中项。
 */
@Composable
fun RuntimeBottomBar(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    if (backdrop != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassBottomBar(
                selected = selected,
                onNavigate = onNavigate,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(64.dp),
            )
        }
    } else {
        StandardBottomBar(selected, onNavigate, modifier)
    }
}

@Composable
private fun StandardBottomBar(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        MainDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigate(destination)
                },
                icon = { RuntimeIcon(destination.icon, Modifier.size(24.dp)) },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * 液态玻璃悬浮底部导航：完全对齐 AndroidLiquidGlass 的 LiquidBottomTabs。
 * 采用三层透镜折射架构：
 * 1. 底层面板：胶囊型玻璃底板，带 vibrancy() + 8dp blur + 24dp lens + InteractiveHighlight 手指跟随高光
 * 2. 染色层：捕获为 tabsBackdrop，高亮染色图标/文本
 * 3. 顶层指示水滴：使用 rememberCombinedBackdrop(backdrop, tabsBackdrop) 同时折射底图与染色层，
 *    辅以 DampedDragAnimation 弹性物理挤压拉伸（Squash & Stretch）和色散透镜（chromaticAberration）。
 */
@Composable
private fun LiquidGlassBottomBar(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainDestination.entries
    val tabsCount = destinations.size
    val capsuleShape = RoundedCornerShape(percent = 50)
    val isLightTheme = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
    else Color(0xFF121212).copy(alpha = 0.4f)
    val contentColor = if (isLightTheme) Color.Black else Color.White

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val currentSelected by rememberUpdatedState(selected)
        val currentOnNavigate by rememberUpdatedState(onNavigate)
        val currentTabWidth by rememberUpdatedState(tabWidth)
        val currentIsLtr by rememberUpdatedState(isLtr)

        fun selectDestination(index: Int) {
            val destination = destinations.getOrNull(index) ?: return
            if (destination != currentSelected) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnNavigate(destination)
            }
        }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selected.ordinal.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
            )
        }

        dampedDragAnimation.onDragStopped = {
            val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
            animateToValue(targetIndex.toFloat())
            animationScope.launch {
                offsetAnimation.animateTo(
                    0f,
                    spring(1f, 300f, 0.5f)
                )
            }
            selectDestination(targetIndex)
        }

        dampedDragAnimation.onDrag = { _, dragAmount ->
            updateValue(
                (targetValue + dragAmount.x / currentTabWidth * if (currentIsLtr) 1f else -1f)
                    .fastCoerceIn(0f, (tabsCount - 1).toFloat())
            )
            animationScope.launch {
                offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
            }
        }

        LaunchedEffect(selected) {
            val targetOrdinal = selected.ordinal.toFloat()
            if (abs(dampedDragAnimation.targetValue - targetOrdinal) > 0.001f) {
                dampedDragAnimation.animateToValue(targetOrdinal)
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (currentIsLtr) (dampedDragAnimation.value + 0.5f) * currentTabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * currentTabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // 第 1 层：底板 Row（物理玻璃面板 + 手指跟随径向高光）
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                Column(
                    Modifier
                        .clip(capsuleShape)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                        ) {
                            selectDestination(index)
                        }
                        .fillMaxHeight()
                        .weight(1f)
                        .graphicsLayer {
                            val scale = lerp(1f, 1.06f, dampedDragAnimation.pressProgress)
                            scaleX = scale
                            scaleY = scale
                            // 当指示器覆盖此 Tab 时淡出 Layer 1 图标，防止与 tabsBackdrop
                            // 染色图标在玻璃内双层叠加。
                            // 几何：指示器宽 = 1 个 tabWidth，覆盖 Tab N 的条件是 |value - N| < 0.5。
                            // 加 ±0.1 的淡出过渡带：distance ∈ [0.4, 0.6) 时平滑淡入/淡出。
                            val distance = abs(dampedDragAnimation.value - index)
                            alpha = ((distance - 0.40f) / 0.20f).fastCoerceIn(0f, 1f)
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RuntimeIcon(destination.icon, Modifier.size(22.dp), tint = contentColor)
                    Text(
                        stringResource(destination.labelRes),
                        color = contentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        // 第 2 层：染色捕获层（用于指示器透镜进行双折射提取，带 accentColor 染色）
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(
                            24.dp.toPx() * progress,
                            24.dp.toPx() * progress
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                Column(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .graphicsLayer {
                            val scale = lerp(1f, 1.06f, dampedDragAnimation.pressProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RuntimeIcon(destination.icon, Modifier.size(22.dp), tint = accentColor)
                    Text(
                        stringResource(destination.labelRes),
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        // 第 3 层：顶层指示水滴（使用 rememberCombinedBackdrop 同时折射背景与染色层）
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { capsuleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        // 仅允许水平方向的 Squash & Stretch（拉宽），
                        // 不做垂直方向的压缩（scaleY 减小）。
                        // 原因：scaleY 缩减会使指示器玻璃高度低于 Layer 1 图标高度，
                        // 导致图标顶部/底部超出玻璃覆盖范围，暴露出未染色的原色图标，
                        // 形成"着色图标 + 未着色图标分层"的视觉问题。
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        // scaleY 只保留来自 press 动画的缩放，不叠加 velocity 压缩
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/** Marks a page-content layer as the safe source for liquid-glass consumers. */
@Composable
fun Modifier.liquidGlassContent(): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
    return if (backdrop != null) layerBackdrop(backdrop) else this
}

/** Returns whether the current composition is hosted by the Chengming glass theme. */
@Composable
fun isLiquidGlassThemeActive(): Boolean = LocalLiquidGlassBackdrop.current != null

/**
 * 天玄品牌 TopBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    statusText: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val glassBackdrop = LocalLiquidGlassSurfaceBackdrop.current
    val glassSurfaceColor = MaterialTheme.colorScheme.surface
    val glassShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val topBar: @Composable () -> Unit = {
        TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onBack == null) TianXuanBrandBadge(30.dp)
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (statusText != null) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                RuntimeIconButton(onClick = onBack, contentDescription = stringResource(R.string.components_back)) {
                    RuntimeIcon(RuntimeIconName.Back, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (glassBackdrop == null) MaterialTheme.colorScheme.background else Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        )
    }
    if (glassBackdrop == null) {
        Box(modifier = modifier.fillMaxWidth()) { topBar() }
    } else {
        val density = LocalDensity.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = glassBackdrop,
                    shape = { glassShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx(), edgeTreatment = TileMode.Mirror)
                        lens(14.dp.toPx(), 22.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 8.dp, alpha = 0.08f) },
                    innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.06f) },
                    onDrawSurface = {
                        drawRoundRect(glassSurfaceColor.copy(alpha = 0.28f))
                    },
                ),
        ) { topBar() }
    }
}

/**
 * 天玄品牌标志徽章
 */
@Composable
fun TianXuanBrandBadge(size: Dp = 38.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Color.White)
            .padding(size * 0.10f),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.components_tianxuan_logo),
            contentDescription = stringResource(R.string.components_tianxuan_logo),
            modifier = Modifier.size(size * 0.80f),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 天玄精制卡片组件：支持自适应表面色与细腻描边
 */
@Composable
fun RuntimeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    exportedBackdrop: LayerBackdrop? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassBackdrop = LocalLiquidGlassSurfaceBackdrop.current
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = borderColor.takeIf { it.alpha > 0f }?.let { BorderStroke(1.dp, it) }

    if (glassBackdrop != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.985f else 1f,
            animationSpec = tween(180, easing = FastOutSlowInEasing),
            label = "glassCardScale",
        )
        val density = LocalDensity.current
        val glassModifier = modifier
            .drawBackdrop(
                backdrop = glassBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(LiquidGlassLevel.Thick.blurRadius.toPx(), edgeTreatment = TileMode.Mirror)
                    lens(
                        refractionHeight = LiquidGlassLevel.Thick.lensMin.toPx(),
                        refractionAmount = LiquidGlassLevel.Thick.lensMax.toPx(),
                        depthEffect = LiquidGlassLevel.Thick.depthEffect,
                    )
                },
                highlight = { Highlight.Default.copy(alpha = LiquidGlassLevel.Thick.highlightAlpha) },
                shadow = { Shadow(radius = LiquidGlassLevel.Thick.shadowRadius, alpha = LiquidGlassLevel.Thick.shadowAlpha) },
                innerShadow = { InnerShadow(radius = LiquidGlassLevel.Thick.innerShadowRadius, alpha = LiquidGlassLevel.Thick.innerShadowAlpha) },
                exportedBackdrop = exportedBackdrop,
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    val safeAlpha = if (containerColor.alpha == 1f) 0.22f else minOf(containerColor.alpha, 0.28f)
                    drawRoundRect(containerColor.copy(alpha = safeAlpha))
                },
            )
            .clip(shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            )
        Column(glassModifier.padding(contentPadding), content = content)
    } else if (onClick == null) {

        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * 主题自适应主操作按钮。玄同使用 Material 3；澄明使用参考 Glass 项目的胶囊折射、
 * 按压缩放与半透明染色。
 */
@Composable
fun RuntimeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    shape: Shape = RoundedCornerShape(24.dp),
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        if (tonal) {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors ?: ButtonDefaults.filledTonalButtonColors(),
                border = border,
                contentPadding = contentPadding,
                content = content,
            )
        } else {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors ?: ButtonDefaults.buttonColors(),
                border = border,
                contentPadding = contentPadding,
                content = content,
            )
        }
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val buttonColors = colors ?: if (tonal) {
        ButtonDefaults.filledTonalButtonColors()
    } else {
        ButtonDefaults.buttonColors()
    }
    val foreground = if (enabled) buttonColors.contentColor else buttonColors.disabledContentColor
    val tint = if (enabled) buttonColors.containerColor else buttonColors.disabledContainerColor
    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(radius = 6.dp, alpha = 0.12f) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.10f) },
                layerBlock = if (enabled) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
                        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
                        alpha = if (enabled) 1f else 0.48f
                    }
                } else {
                    { alpha = 0.48f }
                },
                onDrawSurface = {
                    if (tint.isSpecified && tint != Color.Transparent) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = if (tonal) 0.35f else 0.70f))
                    }
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            )
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clip(shape)
            .height(48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { content() }
    }
}

/**
 * 主题自适应开关：
 * 玄同使用 Material 3 标准 Switch；
 * 澄明完全对齐 AndroidLiquidGlass 的 LiquidToggle：
 * - 底轨捕获为 trackBackdrop
 * - 拇指使用 rememberCombinedBackdrop 双层折射，内部随速度挤压拉伸（Squash & Stretch）
 * - 支持触摸拖动和点击切换，自带阻尼弹簧物理（DampedDragAnimation）
 */
@Composable
fun RuntimeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    thumbContent: (@Composable (() -> Unit))? = null,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            thumbContent = thumbContent,
        )
        return
    }

    val isLightTheme = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
    val trackColor = when {
        enabled && checked -> colors.checkedTrackColor
        enabled -> colors.uncheckedTrackColor
        checked -> colors.disabledCheckedTrackColor
        else -> colors.disabledUncheckedTrackColor
    }
    val defaultAccentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val activeColor = if (checked) trackColor else defaultAccentColor
    val inactiveColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val currentIsLtr by rememberUpdatedState(isLtr)
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val dragWidth = with(density) { 20.dp.toPx() }
    val currentDragWidth by rememberUpdatedState(dragWidth)
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.35f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }

    dampedDragAnimation.onDragStopped = {
        if (didDrag) {
            fraction = if (targetValue >= 0.5f) 1f else 0f
            currentOnCheckedChange(fraction == 1f)
            didDrag = false
        } else {
            fraction = if (currentChecked) 0f else 1f
            currentOnCheckedChange(fraction == 1f)
        }
    }

    dampedDragAnimation.onDrag = { _, dragAmount ->
        if (!didDrag) {
            didDrag = dragAmount.x != 0f
        }
        val delta = dragAmount.x / currentDragWidth
        fraction =
            if (currentIsLtr) (fraction + delta).fastCoerceIn(0f, 1f)
            else (fraction - delta).fastCoerceIn(0f, 1f)
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { f ->
                dampedDragAnimation.updateValue(f)
            }
    }
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val capsuleShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .semantics { role = Role.Switch },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 底轨层：捕获为 trackBackdrop
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(capsuleShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(4.dp.toPx(), 8.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.20f) },
                    onDrawSurface = {
                        val currentFraction = dampedDragAnimation.value
                        drawRect(lerpColor(inactiveColor, activeColor, currentFraction))
                    },
                )
                .size(60.dp, 30.dp)
        )

        // 拇指层：折射底轨与背景，物理流体
        Box(
            Modifier
                .graphicsLayer {
                    val currentFraction = dampedDragAnimation.value
                    val padding = 3.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, currentFraction)
                        else lerp(-padding, -(padding + dragWidth), currentFraction)
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        },
                    ),
                    shape = { capsuleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(6.dp.toPx() * (1f - progress))
                        lens(
                            6.dp.toPx() + 4.dp.toPx() * progress,
                            10.dp.toPx() + 4.dp.toPx() * progress,
                            chromaticAberration = true,
                            depthEffect = true,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = 0.14f * progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 0.90f - 0.15f * progress))
                    },
                )
                .size(34.dp, 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            thumbContent?.invoke()
        }
    }
}

@Composable
fun RuntimeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, border = border, contentPadding = contentPadding, content = content)
    } else {
        RuntimeButton(onClick, modifier, enabled, tonal = true, contentPadding = contentPadding, shape = shape, colors = colors, border = border, content = content)
    }
}

@Composable
fun RuntimeFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) = RuntimeButton(onClick, modifier, enabled, tonal = true, contentPadding = contentPadding, shape = shape, colors = colors, border = border, content = content)

@Composable
fun RuntimeTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors, contentPadding = contentPadding, content = content)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val foreground = if (enabled) colors.contentColor else colors.disabledContentColor
        Row(
            modifier = modifier
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(LiquidGlassLevel.UltraThin.blurRadius.toPx())
                        lens(
                            refractionHeight = LiquidGlassLevel.UltraThin.lensMin.toPx(),
                            refractionAmount = LiquidGlassLevel.UltraThin.lensMax.toPx(),
                            depthEffect = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = if (pressed) 0.45f else 0.18f) },
                    innerShadow = { InnerShadow(radius = 2.dp, alpha = if (pressed) 0.08f else 0.04f) },
                    layerBlock = {
                        scaleX = if (pressed) 1.04f else 1f
                        scaleY = if (pressed) 1.04f else 1f
                        alpha = if (enabled) 1f else 0.48f
                    },
                    onDrawSurface = { drawRoundRect(colors.containerColor.copy(alpha = 0.16f)) },
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides foreground) {
                content()
            }
        }
    }
}

@Composable
fun RuntimeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val accessibilityLabel = contentDescription
    val contentColor = MaterialTheme.colorScheme.onSurface
    val glassSurfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)
    if (backdrop == null) {
        // Material fallback：将无障碍标签挂到按钮节点上（Icon 自身 contentDescription 恒为 null）
        IconButton(
            onClick = onClick,
            modifier = modifier.semantics { accessibilityLabel?.let { this.contentDescription = it } },
            enabled = enabled,
            content = content,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.92f else 1f,
            animationSpec = tween(120, easing = FastOutSlowInEasing),
            label = "glassIconScale",
        )
        Box(
            modifier = modifier
                // 玻璃主题分支同样保证 ≥48dp 最小触摸目标（Material 分支由 IconButton 内部保证）
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(LiquidGlassLevel.UltraThin.blurRadius.toPx())
                        lens(
                            refractionHeight = LiquidGlassLevel.UltraThin.lensMin.toPx(),
                            refractionAmount = LiquidGlassLevel.UltraThin.lensMax.toPx(),
                            depthEffect = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = if (pressed) 0.40f else 0.25f) },
                    shadow = { Shadow(radius = 4.dp, alpha = 0.08f) },
                    innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.08f) },
                    layerBlock = {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (enabled) 1f else 0.45f
                    },
                    onDrawSurface = { drawCircle(glassSurfaceColor) },
                )
                .semantics {
                    accessibilityLabel?.let { this.contentDescription = it }
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,

        ) { CompositionLocalProvider(LocalContentColor provides contentColor, content = content) }
    }
}

/**
 * 主题自适应线性进度条：
 * 玄同使用 Material 3 基础样式；
 * 澄明使用拟真液态玻璃水槽（Liquid Glass Tube）与流动水珠（Fluid Bead）：
 * - 底层：通透的凹面玻璃滑槽，带景深折射、SDF高光与内凹微阴影
 * - 流体层：具有色彩饱和度与透光渐变的液态填充物
 * - 前端水滴：浮动在进度顶端的晶莹液态水珠，随进度推进并带弹性缩放与色散
 */
@Composable
fun RuntimeLinearProgressIndicator(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        if (progress == null) androidx.compose.material3.LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
        else androidx.compose.material3.LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor)
        return
    }

    val fraction = if (progress != null) {
        progress().coerceIn(0f, 1f)
    } else {
        val indeterminateTransition = rememberInfiniteTransition(label = "glassLinearProgress")
        val indeterminateFraction by indeterminateTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glassIndeterminateFraction",
        )
        indeterminateFraction
    }

    val tubeShape = RoundedCornerShape(percent = 50)
    val beadShape = CircleShape

    BoxWithConstraints(
        modifier = modifier
            .height(14.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val totalWidth = constraints.maxWidth.toFloat()
        val tubeHeight = 6.dp
        val beadSize = 10.dp

        // 1. 玻璃水槽底轨（Liquid Glass Tube）
        Box(
            Modifier
                .fillMaxWidth()
                .height(tubeHeight)
                .clip(tubeShape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { tubeShape },
                    effects = {
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(4.dp.toPx(), 8.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.28f) },
                    shadow = { Shadow(radius = 2.dp, alpha = 0.08f) },
                    innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.12f) },
                    onDrawSurface = { drawRoundRect(trackColor.copy(alpha = 0.25f)) },
                ),
        )

        // 2. 内部液态填充条（Fluid Stream）
        if (fraction > 0.01f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(tubeHeight)
                    .clip(tubeShape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { tubeShape },
                        effects = {
                            lens(4.dp.toPx(), 6.dp.toPx(), chromaticAberration = true, depthEffect = true)
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.35f) },
                        innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.15f) },
                        onDrawSurface = {
                            drawRoundRect(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.45f),
                                        color.copy(alpha = 0.85f),
                                    )
                                )
                            )
                        },
                    ),
            )
        }

        // 3. 顶端浮动液态水珠（Fluid Bead）
        if (fraction > 0.02f) {
            val beadOffset = with(LocalDensity.current) {
                ((totalWidth * fraction) - beadSize.toPx() / 2f)
                    .coerceIn(0f, totalWidth - beadSize.toPx())
                    .toDp()
            }
            Box(
                Modifier
                    .offset(x = beadOffset)
                    .size(beadSize)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { beadShape },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(6.dp.toPx(), 10.dp.toPx(), chromaticAberration = true, depthEffect = true)
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow(radius = 4.dp, alpha = 0.18f) },
                        innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.20f) },
                        onDrawSurface = {
                            drawCircle(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.90f),
                                        color.copy(alpha = 0.95f),
                                    )
                                )
                            )
                        },
                    ),
            )
        }
    }
}

/**
 * 主题自适应圆形进度环：
 * 玄同使用 Material 3 标准环；
 * 澄明使用立体玻璃环槽（Glass Ring Tube）与环形流动水滴：
 * 环形导轨通透折射背景，前端水珠在环中滑行并发出流动高光。
 */
@Composable
fun RuntimeCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Transparent,
    strokeWidth: Dp = 4.5.dp,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth,
        )
        return
    }

    val infinite = rememberInfiniteTransition(label = "glassCircularProgress")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "glassCircularRotation",
    )
    val sweepAngle by infinite.animateFloat(
        initialValue = 60f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glassCircularSweep",
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(4.dp.toPx(), 8.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.30f) },
                shadow = { Shadow(radius = 4.dp, alpha = 0.12f) },
                innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.10f) },
                onDrawSurface = {
                    val stroke = strokeWidth.toPx()
                    // 1. 环形玻璃导轨底色
                    drawArc(
                        color = if (trackColor != Color.Transparent) trackColor.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.12f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    // 2. 流动液态弧线
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                color.copy(alpha = 0.15f),
                                color.copy(alpha = 0.60f),
                                color.copy(alpha = 0.95f),
                            )
                        ),
                        startAngle = rotation,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    // 3. 头部高亮水滴光晕
                    val rad = Math.toRadians((rotation + sweepAngle).toDouble())
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val r = (size.width - stroke) / 2f
                    val headX = centerOffset.x + (r * Math.cos(rad)).toFloat()
                    val headY = centerOffset.y + (r * Math.sin(rad)).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = stroke * 0.75f,
                        center = Offset(headX, headY),
                    )
                },
            ),
    )
}

@Composable
fun RuntimeCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = colors)
        return
    }
    val primary = when {
        enabled && checked -> colors.checkedBoxColor
        enabled -> colors.uncheckedBorderColor
        checked -> colors.disabledCheckedBoxColor
        else -> colors.disabledUncheckedBorderColor
    }
    val checkmark = if (enabled) colors.checkedCheckmarkColor else colors.disabledCheckedBoxColor
    Box(
        modifier
            .size(24.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(8.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(8.dp) },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(4.dp.toPx(), 6.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.34f) },
                innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.10f) },
                onDrawSurface = {
                    drawRoundRect(primary.copy(alpha = if (checked) 0.85f else 0.12f))
                    if (!checked) drawRoundRect(primary.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
                },
            )
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = checkmark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RuntimeRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled, colors = colors)
        return
    }
    val primary = when {
        enabled && selected -> colors.selectedColor
        enabled -> colors.unselectedColor
        selected -> colors.disabledSelectedColor
        else -> colors.disabledUnselectedColor
    }
    Box(
        modifier
            .size(24.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(4.dp.toPx(), 6.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.30f) },
                innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.10f) },
                onDrawSurface = {
                    drawCircle(primary.copy(alpha = 0.1f))
                    drawCircle(primary.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
                },
            )
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(11.dp).background(primary, CircleShape))
    }
}

/**
 * 主题自适应滑块：
 * 玄同使用 Material 3 标准 Slider；
 * 澄明完全对齐 AndroidLiquidGlass 的 LiquidSlider：
 * - 底轨捕获为 trackBackdrop（包含未激活底轨与彩色激活轨）
 * - 滑块拇指使用 rememberCombinedBackdrop(backdrop, trackBackdrop) 实时折射底层彩色滑轨
 * - 结合 DampedDragAnimation：拖动速度驱动拇指弹性形变（Squash & Stretch），松手平滑惯性阻尼回弹
 */
@Composable
fun RuntimeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
        )
        return
    }

    val isLightTheme = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
    val accentColor = if (colors.activeTrackColor.isSpecified && colors.activeTrackColor != Color.Unspecified) {
        colors.activeTrackColor
    } else if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)

    val trackColor = if (colors.inactiveTrackColor.isSpecified && colors.inactiveTrackColor != Color.Unspecified) {
        colors.inactiveTrackColor
    } else if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()
    val capsuleShape = RoundedCornerShape(percent = 50)

    val rangeSize = valueRange.endInclusive - valueRange.start
    fun quantize(v: Float): Float {
        if (steps <= 0) return v.coerceIn(valueRange)
        val stepSize = rangeSize / (steps + 1)
        val stepIndex = ((v - valueRange.start) / stepSize).roundToInt()
        return (valueRange.start + stepIndex * stepSize).coerceIn(valueRange)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth.toFloat()
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val currentTrackWidth by rememberUpdatedState(trackWidth)
        val currentIsLtr by rememberUpdatedState(isLtr)
        val currentEnabled by rememberUpdatedState(enabled)
        val currentOnValueChange by rememberUpdatedState(onValueChange)
        val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
        val animationScope = rememberCoroutineScope()
        var isDragging by remember { mutableStateOf(false) }
        var currentDragValue by remember { mutableFloatStateOf(value) }

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
            )
        }

        dampedDragAnimation.onDragStarted = {
            if (currentEnabled) {
                isDragging = true
                currentDragValue = value
            }
        }

        dampedDragAnimation.onDragStopped = {
            if (isDragging) {
                val finalVal = quantize(currentDragValue)
                currentOnValueChange(finalVal)
                currentOnValueChangeFinished?.invoke()
                isDragging = false
            }
        }

        dampedDragAnimation.onDrag = { _, dragAmount ->
            if (currentEnabled && currentTrackWidth > 0f) {
                isDragging = true
                val delta = rangeSize * (dragAmount.x / currentTrackWidth) * if (currentIsLtr) 1f else -1f
                currentDragValue = (currentDragValue + delta).coerceIn(valueRange)
                updateValue(currentDragValue)
                val nextQuantized = quantize(currentDragValue)
                currentOnValueChange(nextQuantized)
            }
        }

        LaunchedEffect(value) {
            if (!isDragging) {
                currentDragValue = value
                if (dampedDragAnimation.targetValue != value) {
                    dampedDragAnimation.updateValue(value)
                }
            }
        }

        // 1. 底轨捕获层：支持 40dp 高度全触摸区域点击，捕获为 trackBackdrop
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(animationScope, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { position ->
                        if (trackWidth > 0f) {
                            val fraction = (position.x / trackWidth).coerceIn(0f, 1f)
                            val targetRaw = if (isLtr) {
                                valueRange.start + fraction * rangeSize
                            } else {
                                valueRange.endInclusive - fraction * rangeSize
                            }
                            val finalVal = quantize(targetRaw)
                            currentDragValue = finalVal
                            dampedDragAnimation.animateToValue(finalVal)
                            onValueChange(finalVal)
                            onValueChangeFinished?.invoke()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .fillMaxWidth()
                    .height(6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 未激活底轨（半透明灰）
                Box(
                    Modifier
                        .clip(capsuleShape)
                        .background(trackColor)
                        .fillMaxSize()
                )

                // 激活高亮填充轨（动态裁剪宽度）
                Box(
                    Modifier
                        .clip(capsuleShape)
                        .background(accentColor)
                        .fillMaxHeight()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val progress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                            val width = (constraints.maxWidth * progress).fastRoundToInt()
                            layout(width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }
        }

        // 2. 液态玻璃拇指水滴（完全对齐示例项目：双折射 + Ambient透镜高光 + 速度形变 + 白玉融化为折射水滴）
        Box(
            Modifier
                .graphicsLayer {
                    val progress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                    translationX =
                        (-size.width / 2f + trackWidth * progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { capsuleShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

/** Alert surface that stays Material in Xuantong and becomes a backdrop lens in Chengming. */
@Composable
fun RuntimeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            title = title,
            text = text,
            icon = icon,
        )
        return
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(32.dp)
        val isLightTheme = MaterialTheme.colorScheme.onSurface.luminance() < 0.5f
        val dialogSurfaceColor = if (isLightTheme) Color(0xFFFCFCFD).copy(alpha = 0.92f)
        else Color(0xFF18181C).copy(alpha = 0.88f)
        val borderColor = if (isLightTheme) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.18f)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .wrapContentHeight(Alignment.Top)
                .imePadding()
                .heightIn(max = 560.dp)
                .shadow(elevation = 24.dp, shape = shape, spotColor = Color.Black.copy(alpha = 0.30f))
                .clip(shape)
                .background(dialogSurfaceColor)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(borderColor, borderColor.copy(alpha = 0.08f)),
                    ),
                    shape = shape,
                )
                .drawWithContent {
                    drawContent()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, borderColor, Color.Transparent),
                        ),
                        start = Offset(24.dp.toPx(), 0.5f),
                        end = Offset(size.width - 24.dp.toPx(), 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompositionLocalProvider(LocalLiquidGlassSurfaceBackdrop provides null) {
                if (icon != null || title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        icon?.invoke()
                        title?.invoke()
                    }
                }
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    text?.invoke()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}


/**
 * 呼吸状态指示药丸
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val alphaAnim = if (pulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        ).value
    } else 1f

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alphaAnim)),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun IconTile(
    icon: RuntimeIconName,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 42.dp,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val shape = RoundedCornerShape(12.dp)
    if (backdrop == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(size * 0.48f), tint = color)
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(4.dp.toPx(), 8.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.25f) },
                    innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.10f) },
                    onDrawSurface = { drawRoundRect(color.copy(alpha = 0.12f)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(size * 0.48f), tint = color)
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isCode: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.62f, fill = false),
        )
    }
}

@Composable
fun CodeBlockRow(
    label: String,
    code: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val backdrop = LocalLiquidGlassSurfaceBackdrop.current
        val shape = RoundedCornerShape(8.dp)
        val codeModifier = if (backdrop == null) {
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(4.dp.toPx(), 6.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.20f) },
                    onDrawSurface = { drawRoundRect(Color.Black.copy(alpha = 0.20f)) },
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        }
        Box(modifier = codeModifier) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun EmptyPanel(
    icon: RuntimeIconName,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon, color = MaterialTheme.colorScheme.primary, size = 48.dp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    val bannerModifier = if (backdrop == null) {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(6.dp.toPx(), 10.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.25f) },
                shadow = { Shadow(radius = 4.dp, alpha = 0.08f) },
                onDrawSurface = { drawRoundRect(color.copy(alpha = 0.12f)) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    }
    Row(
        modifier = bannerModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeIcon(if (isError) RuntimeIconName.Alert else RuntimeIconName.Check, Modifier.size(17.dp), color)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            val dismissLabel = stringResource(R.string.components_close)
            RuntimeIconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
                contentDescription = dismissLabel,
            ) {
                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 🌟 滚动列表平滑渐隐遮罩 (Scroll Fading Edge Mask)
 * 为列表顶部和底部提供平滑的 Alpha 渐隐过渡，消除与 TopBar / BottomBar 之间的生硬切边。
 *
 * ⚠️ 性能提示（2026-09-14）：本实现依赖 `CompositingStrategy.Offscreen` ——
 * `BlendMode.DstIn` 要求先渲染到独立离屏缓冲才能做遮罩。挂在 LazyColumn 上时，
 * **滚动期间每帧都要全量离屏合成**，屏幕越大代价越高，表现为「静止不卡、快速滑动明显掉帧」。
 * 消息列表已改用 [ScrollFadeOverlay]（列表外叠加渐变，代价近乎为零）。
 * 仅在「非滚动、或面积很小」的场景继续使用本修饰符。
 */
fun Modifier.scrollFadingEdge(
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
): Modifier = this.graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val topPx = top.toPx()
    val bottomPx = bottom.toPx()
    val h = size.height

    if (topPx > 0f && h > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = topPx.coerceAtMost(h),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
    if (bottomPx > 0f && h > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = (h - bottomPx).coerceAtLeast(0f),
                endY = h,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * 🌟 滚动渐隐遮罩（轻量版）：绘制为**叠加在滚动内容之上**的两段渐变，不使用离屏合成。
 *
 * 与 [scrollFadingEdge] 的取舍：
 *  - scrollFadingEdge 用 DstIn 真正「擦除」内容 alpha（效果更干净），
 *    但要求 Offscreen 合成 → 滚动每帧全量离屏渲染，是滑动卡顿的头号来源。
 *  - 本实现改为「在内容上方叠一层从背景色到透明的渐变」，视觉近似（顶/底自然淡出），
 *    但**零离屏合成开销**，适合挂在长列表这类滚动频繁、面积大的场景。
 *
 * 用法：把滚动内容与本 Overlay 放进同一个 Box，Overlay 位于内容之上：
 * ```
 * Box {
 *     LazyColumn(...)
 *     ScrollFadeOverlay(top = 4.dp, bottom = 20.dp)
 * }
 * ```
 */
@Composable
fun ScrollFadeOverlay(
    modifier: Modifier = Modifier,
    top: Dp = 14.dp,
    bottom: Dp = 14.dp,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Box(modifier.fillMaxSize()) {
        if (top > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(top)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(color, color.copy(alpha = 0f))),
                    ),
            )
        }
        if (bottom > 0.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bottom)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0f), color)),
                    ),
            )
        }
    }
}

/**
 * 主题自适应悬浮操作按钮 (M3 Floating Action Button)：
 * - Material You 下：遵循 M3 规范，尺寸 56dp，形状 RoundedCornerShape(16.dp)，默认 primaryContainer / onPrimaryContainer 配色与 3dp 色调海拔；
 * - 澄明（液态玻璃）下：透镜折射底层流光、SDF 高光与按压惯性形变。
 */
@Composable
fun RuntimeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 3.dp,
        pressedElevation = 6.dp,
    ),
    content: @Composable () -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            content = content,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.94f else 1f,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            label = "glassFabScale",
        )
        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .size(56.dp)
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(LiquidGlassLevel.Regular.blurRadius.toPx())
                        lens(
                            refractionHeight = LiquidGlassLevel.Regular.lensMin.toPx(),
                            refractionAmount = LiquidGlassLevel.Regular.lensMax.toPx(),
                            depthEffect = LiquidGlassLevel.Regular.depthEffect,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = LiquidGlassLevel.Regular.highlightAlpha) },
                    shadow = { Shadow(radius = LiquidGlassLevel.Regular.shadowRadius, alpha = LiquidGlassLevel.Regular.shadowAlpha) },
                    innerShadow = { InnerShadow(radius = LiquidGlassLevel.Regular.innerShadowRadius, alpha = LiquidGlassLevel.Regular.innerShadowAlpha) },
                    layerBlock = {
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRoundRect(containerColor, blendMode = BlendMode.Hue)
                        drawRoundRect(containerColor.copy(alpha = 0.55f))
                    },
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}

/**
 * 主题自适应小型悬浮操作按钮 (M3 Small Floating Action Button)：
 * 遵循 M3 规范：尺寸 40dp，形状 RoundedCornerShape(12.dp)。
 */
@Composable
fun RuntimeSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 3.dp,
        pressedElevation = 6.dp,
    ),
    content: @Composable () -> Unit,
) {
    RuntimeFloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        content = content,
    )
}

/**
 * 主题自适应过滤标签 (M3 Filter Chip)：
 * - 遵循 M3 规范：高度 32dp，形状 RoundedCornerShape(8.dp)，文字 labelMedium；
 * - 包含 minimumInteractiveComponentSize() 满足 48dp 触控标准；
 * - 澄明主题下自适应磨砂玻璃与按压缩放。
 */
@Composable
fun RuntimeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    if (backdrop == null) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = tween(120, easing = FastOutSlowInEasing),
            label = "glassChipScale",
        )
        val containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.20f)
        }
        val contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

        Box(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .height(32.dp)
                .clip(shape)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(LiquidGlassLevel.UltraThin.blurRadius.toPx())
                        lens(
                            refractionHeight = LiquidGlassLevel.UltraThin.lensMin.toPx(),
                            refractionAmount = LiquidGlassLevel.UltraThin.lensMax.toPx(),
                            depthEffect = LiquidGlassLevel.UltraThin.depthEffect,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = if (selected) 0.38f else 0.18f) },
                    innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.08f) },
                    layerBlock = {
                        scaleX = scale
                        scaleY = scale
                        alpha = if (enabled) 1f else 0.45f
                    },
                    onDrawSurface = {
                        if (selected) {
                            drawRoundRect(containerColor, blendMode = BlendMode.Hue)
                            drawRoundRect(containerColor.copy(alpha = 0.45f))
                        } else {
                            drawRoundRect(containerColor)
                            if (borderColor != Color.Transparent) {
                                drawRoundRect(borderColor, style = Stroke(1.dp.toPx()))
                            }
                        }
                    },
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                    } else if (selected) {
                        Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentColor)
                    }
                    label()
                    trailingIcon?.invoke()
                }
            }
        }
    }
}

