package top.wkbin.tianxuan.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.window.PopupProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.wkbin.tianxuan.ui.components.GlassEffects.liquidGlassSurface
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop
import kotlin.math.abs

/** 全圆角药丸形状 (Capsule Shape) */
val CapsuleShape: Shape = RoundedCornerShape(percent = 50)

// ═══════════════════════════════════════════════════════════════════════════
// 1. RuntimeSegmentedControl（iOS 药丸分段选择器）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃分段选择器 (Segmented Control)。
 *
 * 遵循 Apple HIG Materials 与 Kyant Backdrop 铁律：
 * - 底轨：UltraThin 材质，带有 1px 细微高光边缘与柔和折射；
 * - 拇指指示器：Thin 双重折射材质，借助 rememberCombinedBackdrop(backdrop, trackBackdrop)
 *   同步折射底轨与底层流光，在切换时呈现真实的色散 (chromaticAberration) 与弹性动力学；
 * - 玄同（Material You）主题下自适应降级为原生 M3 药丸指示分段控制。
 */
@Composable
fun <T> RuntimeSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
) {
    if (items.isEmpty()) return

    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val haptic = LocalHapticFeedback.current

    if (backdrop != null) {
        val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
        val trackBackdrop = rememberLayerBackdrop()

        BoxWithConstraints(
            modifier = modifier
                .clip(CapsuleShape)
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = { CapsuleShape },
                    level = LiquidGlassLevel.UltraThin,
                    onDrawSurface = {
                        drawRect(
                            if (isDark) Color(0xFF141414).copy(alpha = 0.35f)
                            else Color(0xFFF5F5F7).copy(alpha = 0.50f)
                        )
                    },
                )
                // 顶部 1px 细微镜面受光天顶线
                .drawWithContent {
                    drawContent()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.25f else 0.55f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(16.dp.toPx(), 0.5f),
                        end = Offset(size.width - 16.dp.toPx(), 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(3.dp)
                .height(38.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val totalWidth = constraints.maxWidth.toFloat()
            val segmentWidth = totalWidth / items.size
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

            // 弹性动力学滑块位置插值
            val animatedIndex by animateFloatAsState(
                targetValue = selectedIndex.toFloat().coerceIn(0f, (items.size - 1).toFloat()),
                animationSpec = LiquidSpringSpec,
                label = "SegmentedThumbOffset",
            )

            // 1. 底轨内容捕获层（供滑块透镜叠加折射）
            Box(
                Modifier
                    .layerBackdrop(trackBackdrop)
                    .fillMaxWidth()
                    .height(32.dp),
            )

            // 2. 悬浮液态玻璃滑块拇指 (Sliding Glass Thumb)
            val thumbOffset = animatedIndex * segmentWidth
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = if (isLtr) thumbOffset else -thumbOffset
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                        shape = { CapsuleShape },
                        effects = {
                            blur(4.dp.toPx())
                            lens(
                                refractionHeight = 8.dp.toPx(),
                                refractionAmount = 14.dp.toPx(),
                                chromaticAberration = true,
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.40f) },
                        shadow = { Shadow(radius = 4.dp, alpha = 0.12f) },
                        innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.08f) },
                        layerBlock = {
                            // 运动过程中的动态微拉伸 (Squash & Stretch)
                            val diff = abs(animatedIndex - selectedIndex)
                            scaleX = 1f + (diff * 0.12f).fastCoerceIn(0f, 0.18f)
                            scaleY = 1f - (diff * 0.06f).fastCoerceIn(0f, 0.10f)
                        },
                        onDrawSurface = {
                            drawRect(
                                if (isDark) Color.White.copy(alpha = 0.16f)
                                else Color.White.copy(alpha = 0.85f),
                            )
                        },
                    )
                    .width(with(LocalDensity.current) { segmentWidth.toDp() })
                    .height(32.dp),
            )

            // 3. 点击响应层与文字标签
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CapsuleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = enabled,
                                role = Role.Tab,
                            ) {
                                if (index != selectedIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(index)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        itemContent(item, isSelected)
                    }
                }
            }
        }
    } else {
        // 玄同主题：原生 Material 3 药丸分段条降级
        Surface(
            modifier = modifier
                .clip(CapsuleShape)
                .height(38.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = CapsuleShape,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val totalWidth = constraints.maxWidth.toFloat()
                val segmentWidth = totalWidth / items.size
                val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

                val animatedIndex by animateFloatAsState(
                    targetValue = selectedIndex.toFloat().coerceIn(0f, (items.size - 1).toFloat()),
                    animationSpec = LiquidSpringSpec,
                    label = "M3SegmentedOffset",
                )

                // M3 实体滑块
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = if (isLtr) animatedIndex * segmentWidth else -animatedIndex * segmentWidth
                        }
                        .width(with(LocalDensity.current) { segmentWidth.toDp() })
                        .height(32.dp),
                    shape = CapsuleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CapsuleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled,
                                    role = Role.Tab,
                                ) {
                                    if (index != selectedIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(index)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            itemContent(item, isSelected)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 字符型选项分段选择器重载（最常见的文本分段）。
 */
@Composable
fun RuntimeSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RuntimeSegmentedControl(
        items = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    ) { text, isSelected ->
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. RuntimeOutlinedTextField & RuntimeTextField（iOS 内嵌凹陷折射文本框）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃内嵌文本输入框 (RuntimeOutlinedTextField)。
 *
 * 在澄明主题下：
 * - 采用 UltraThin 材质渲染内嵌折射玻璃，内嵌 2dp 柔和内阴影 (InnerShadow) 形成微微凹陷质感；
 * - 边框为 1px 超细镜面反射线，获得焦点时平滑过渡到品牌高光强调色与流体外发光；
 * - 嵌套于卡片、弹窗或抽屉中时，安全消费 LocalLiquidGlassSurfaceBackdrop，避免过度折射；
 * 在玄同主题下：
 * - 100% 降级为原生 androidx.compose.material3.OutlinedTextField。
 */
@Composable
fun RuntimeOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    if (backdrop == null) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = actualInteractionSource,
            shape = shape,
            colors = colors,
        )
        return
    }

    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val isFocused by actualInteractionSource.collectIsFocusedAsState()

    val accentColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val unfocusedBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    val currentBorderColor = if (isFocused) accentColor else unfocusedBorderColor
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Column(modifier = modifier) {
        // 可选顶部标签
        if (label != null) {
            Box(
                Modifier
                    .padding(start = 4.dp, bottom = 4.dp)
                    .graphicsLayer {
                        alpha = if (isFocused) 1f else 0.75f
                    },
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                ) {
                    label()
                }
            }
        }

        // 输入框玻璃主体
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = { shape },
                    level = LiquidGlassLevel.UltraThin,
                    onDrawSurface = {
                        drawRect(
                            if (isDark) Color(0xFF181818).copy(alpha = 0.38f)
                            else Color(0xFFF2F2F7).copy(alpha = 0.50f),
                        )
                    },
                )
                .border(borderWidth, currentBorderColor, shape)
                // 顶部 1px 细微内沿高光
                .drawWithContent {
                    drawContent()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.25f else 0.60f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(12.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 12.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    ) {
                        leadingIcon()
                    }
                    Spacer(Modifier.width(8.dp))
                }

                if (prefix != null) {
                    prefix()
                    Spacer(Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 占位符 (Placeholder)
                    if (value.isEmpty() && placeholder != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            LocalTextStyle provides textStyle,
                        ) {
                            placeholder()
                        }
                    }

                    // 原生文本编辑引擎
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        minLines = minLines,
                        interactionSource = actualInteractionSource,
                        cursorBrush = SolidColor(accentColor),
                    )
                }

                if (suffix != null) {
                    Spacer(Modifier.width(4.dp))
                    suffix()
                }

                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    ) {
                        trailingIcon()
                    }
                }
            }
        }

        // 底部辅助/错误文本
        if (supportingText != null) {
            Box(Modifier.padding(start = 4.dp, top = 4.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                ) {
                    supportingText()
                }
            }
        }
    }
}

/**
 * 填充型文本输入框别名，在液态玻璃下与 OutlinedTextField 共享统一高品质质感。
 */
@Composable
fun RuntimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    RuntimeOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 3. RuntimeModalBottomSheet（iOS 浮动玻璃半屏抽屉）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃模态底部抽屉 (RuntimeModalBottomSheet)。
 *
 * 遵循 Glass-on-Glass 铁律与 Apple 抽屉规范：
 * - 抽屉面板采用 Thick 材质，顶端 28dp 大圆角与镜面高光；
 * - 顶部居中带有 Apple 标志性的药丸拖动手柄指示条 (Drag Handle)；
 * - 关键机制：创建专属 sheetBackdrop 并通过 LocalLiquidGlassSurfaceBackdrop 注入，
 *   使抽屉内部所有的卡片、按钮、输入框、分段器自动成为第二层受光玻璃，防止多层抓取导致崩溃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current

    if (backdrop == null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = if (containerColor != Color.Unspecified) containerColor else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle ?: { BottomSheetDefaults.DragHandle() },
            content = content,
        )
        return
    }

    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val sheetBackdrop = rememberLayerBackdrop()

    var isClosing by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val enterExitAnim = remember { Animatable(1f) }
    LaunchedEffect(isClosing) {
        if (!isClosing) {
            enterExitAnim.animateTo(0f, animationSpec = LiquidSpringSpec)
        } else {
            enterExitAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f))
            onDismissRequest()
        }
    }

    val requestClose: () -> Unit = {
        if (!isClosing) {
            isClosing = true
        }
    }

    BackHandler(enabled = true, onBack = requestClose)

    val currentAlpha = (1f - enterExitAnim.value).coerceIn(0f, 1f)
    val actualScrimColor = if (isDark) {
        Color.Black.copy(alpha = 0.45f * currentAlpha)
    } else {
        Color(0xFF1E2028).copy(alpha = 0.25f * currentAlpha)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(150f),
    ) {
        // 全屏半透明遮罩背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(actualScrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = requestClose,
                ),
        )

        // 底部折射玻璃面板
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val slideDistance = size.height.takeIf { it > 0f } ?: 800f
                    translationY = (slideDistance * enterExitAnim.value) + dragOffsetY.coerceAtLeast(0f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > 80.dp.toPx()) {
                                requestClose()
                            } else {
                                dragOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        },
                    )
                }
                .liquidGlassSurface(
                    backdrop = backdrop,
                    shape = { shape },
                    level = LiquidGlassLevel.Thick,
                    exportedBackdrop = sheetBackdrop,
                    onDrawSurface = {
                        drawRect(
                            if (isDark) Color(0xFF101014).copy(alpha = 0.65f)
                            else Color(0xFFFCFCFE).copy(alpha = 0.72f),
                        )
                    },
                )
                // 顶部弧线 1px 镜面反射
                .drawWithContent {
                    drawContent()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (isDark) 0.35f else 0.70f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(24.dp.toPx(), 0.5f),
                        end = Offset(size.width - 24.dp.toPx(), 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            // 向内部所有子级提供安全层级 sheetBackdrop
            CompositionLocalProvider(
                LocalLiquidGlassSurfaceBackdrop provides sheetBackdrop,
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                ) {
                    if (dragHandle != null) {
                        dragHandle()
                    } else {
                        DefaultGlassDragHandle(isDark)
                    }
                    content()
                }
            }
        }
    }
}

/** 默认 Apple 质感药丸拖拽手柄 */
@Composable
private fun DefaultGlassDragHandle(isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CapsuleShape)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.24f)
                    else Color.Black.copy(alpha = 0.16f),
                ),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 4. RuntimeDropdownMenu & RuntimeDropdownMenuItem（iOS 悬浮玻璃上下文菜单）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃悬浮上下文菜单 (RuntimeDropdownMenu)。
 *
 * 在澄明主题下：
 * - 采用 Regular 材质，18dp 平滑圆角，带有 16dp 柔和环境光散射阴影；
 * - 窗体顶部带有镜面高光，内部子项提供 menuBackdrop 隔离；
 * 在玄同主题下：
 * - 降级为原生 M3 DropdownMenu。
 */
@Composable
fun RuntimeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current

    if (backdrop == null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            properties = properties,
            shape = shape,
            content = content,
        )
        return
    }

    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val surfaceColor = if (isDark) Color(0xFF1E1E24).copy(alpha = 0.90f) else Color(0xFFFAFBFC).copy(alpha = 0.92f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.65f)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .shadow(elevation = 16.dp, shape = shape, spotColor = Color.Black.copy(alpha = 0.28f))
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = 0.08f))),
                shape = shape,
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            borderColor,
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(14.dp.toPx(), 0.5f),
                    end = Offset(size.width - 14.dp.toPx(), 0.5f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        offset = offset,
        properties = properties,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        CompositionLocalProvider(
            LocalLiquidGlassSurfaceBackdrop provides null,
        ) {
            content()
        }
    }
}

/**
 * 天玄 · iOS 规范液态玻璃上下文菜单项 (RuntimeDropdownMenuItem)。
 */
@Composable
fun RuntimeDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPressed) {
                    if (isDark) Color.White.copy(alpha = 0.12f)
                    else Color.Black.copy(alpha = 0.08f)
                } else Color.Transparent,
            ),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = actualInteractionSource,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 5. RuntimeSearchBar（iOS 胶囊透镜搜索栏）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃胶囊搜索栏 (RuntimeSearchBar)。
 *
 * - 外观：高度 42dp，Capsule 全圆角，UltraThin 材质轻微下凹折射；
 * - 集成：左侧搜索图标，内部 BasicTextField，右侧渐入渐出的一键清空按钮；
 * - 双主题自适应：在玄同主题下无缝降级为标准圆角 Surface 搜索样式。
 */
@Composable
fun RuntimeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    leadingIcon: (@Composable () -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current
    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val containerModifier = if (backdrop != null) {
        modifier
            .height(42.dp)
            .clip(CapsuleShape)
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = { CapsuleShape },
                level = LiquidGlassLevel.UltraThin,
                onDrawSurface = {
                    drawRect(
                        if (isDark) Color(0xFF18181C).copy(alpha = 0.40f)
                        else Color(0xFFEBEBF0).copy(alpha = 0.55f),
                    )
                },
            )
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary
                else if (isDark) Color.White.copy(alpha = 0.12f)
                else Color.Black.copy(alpha = 0.08f),
                shape = CapsuleShape,
            )
            .drawWithContent {
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.25f else 0.60f),
                            Color.Transparent,
                        ),
                    ),
                    start = Offset(16.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 16.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
    } else {
        modifier
            .height(42.dp)
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CapsuleShape,
            )
    }

    Row(
        modifier = containerModifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧搜索图标
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        } else {
            RuntimeIcon(
                name = RuntimeIconName.Search,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
            )
            Spacer(Modifier.width(8.dp))
        }

        // 搜索输入主体
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(query) }),
                interactionSource = interactionSource,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }

        // 右侧一键清空按钮
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onQueryChange("")
                        onClear?.invoke()
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Close,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 6. RuntimeBadge（iOS 色散水滴角标）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃状态/计数水滴角标 (RuntimeBadge)。
 *
 * 在澄明主题下：
 * - 采用 UltraThin 材质，微型胶囊药丸外观；
 * - 借助 BlendMode.Hue 与真实品牌强调色融合，带来通透水润的玻璃珠质感；
 * 在玄同主题下：
 * - 降级为原生 M3 Badge。
 */
@Composable
fun RuntimeBadge(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current

    if (backdrop == null) {
        Badge(
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content,
        )
        return
    }

    Box(
        modifier = modifier
            .clip(CapsuleShape)
            .liquidGlassSurface(
                backdrop = backdrop,
                shape = { CapsuleShape },
                level = LiquidGlassLevel.UltraThin,
                onDrawSurface = {
                    drawRect(containerColor, blendMode = BlendMode.Hue)
                    drawRect(containerColor.copy(alpha = 0.85f))
                },
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), CapsuleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .heightIn(min = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides contentColor,
                    LocalTextStyle provides MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                ) {
                    content()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 7. RuntimeLiquidButton（iOS 规范纯正液态胶囊按钮）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范液态玻璃胶囊按钮 (RuntimeLiquidButton)。
 *
 * 严格遵从 Kyant Catalog 原版 LiquidButton 物理架构：
 * - 动态漫射高光：内置 AGSL InteractiveHighlight，手指触控位置动态漫射流光；
 * - 速度阻尼拉伸形变：按下与拖拽时根据角度与位移进行 tanh 双轴弹性拉伸；
 * - 光学配置：vibrancy() + blur(2dp) + lens(12dp, 24dp)；
 * - 颜色浸润：支持 BlendMode.Hue 真实色相浸润与 surfaceColor 叠加；
 * - 字体排版：默认使用 AppleTypography.Button（16sp / SemiBold / Tracking -0.2sp）；
 * - 双主题降级：玄同主题下自适应为原生 Material 3 填充/胶囊按钮。
 */
@Composable
fun RuntimeLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    shape: Shape = CapsuleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalLiquidGlassSurfaceBackdrop.current

    if (backdrop == null) {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary,
            ),
            content = content,
        )
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.45f) },
                shadow = { Shadow(radius = 6.dp, alpha = 0.12f) },
                innerShadow = { InnerShadow(radius = 3.dp, alpha = 0.08f) },
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
                    }
                } else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else Modifier,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 8. RuntimeSheetHeader（iOS 规范底部抽屉标准顶栏）
// ═══════════════════════════════════════════════════════════════════════════

/**
 * 天玄 · iOS 规范底部抽屉标准顶栏 (RuntimeSheetHeader)。
 *
 * 遵循 iOS UISheetPresentationController 顶栏规范：
 * - 左侧：取消/关闭动作文本；
 * - 中央：标题文本（加粗 Headline 居中）；
 * - 右侧：完成/确认动作高光文本；
 * - 下边缘：极细微高光反光分割线。
 */
@Composable
fun RuntimeSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirm: (() -> Unit)? = null,
    confirmText: String = "完成",
    dismissText: String = "取消",
) {
    val isDark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左侧取消
            Text(
                text = dismissText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            // 中央标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 右侧确认/完成
            if (onConfirm != null) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .clickable(role = Role.Button, onClick = onConfirm)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // 底部 1px 细微光影分割线
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
