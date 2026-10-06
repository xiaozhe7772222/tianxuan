package top.wkbin.tianxuan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 触控手势侦听器：用于精准捕获触控拖拽起点、位移与抬起事件。
 */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent = drag(
            pointerId = drag.id,
            onDrag = { onDrag(it, it.positionChange()) },
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) return dragEvent
        }
    }
}

/**
 * 液态高光交互类：
 * 使用 AGSL 运行时着色器根据触点实时计算径向漫射高光，在手指按下和拖拽时
 * 沿表面渲染流动的光斑，配合弹性物理实现水滴/液态玻璃独特的触感。
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = if (isRuntimeShaderSupported()) {
        RuntimeShader(
            """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
            """.trimIndent(),
        )
    } else {
        null
    }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            if (shader != null) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus,
                )
                shader.apply {
                    val pos = position(size, positionAnimation.value)
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(0.18f * progress))
                    setFloatUniform("radius", size.minDimension * 1.5f)
                    setFloatUniform(
                        "position",
                        pos.x.fastCoerceIn(0f, size.width),
                        pos.y.fastCoerceIn(0f, size.height),
                    )
                }
                drawRect(
                    ShaderBrush(shader.asComposeShader()),
                    blendMode = BlendMode.Plus,
                )
            } else {
                drawRect(
                    Color.White.copy(0.20f * progress),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/**
 * 阻尼拖拽与弹性缩放物理动画：
 * 完整对齐 AndroidLiquidGlass 的手势物理表现，提供：
 * - 拖动时指示器的阻尼位移与速度跟踪
 * - 按压、拖动时的 scaleX / scaleY 挤压拉伸（Squash & Stretch）
 * - 松手后的惯性回弹与弹簧物理
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    var onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    var onDragStopped: DampedDragAnimation.() -> Unit,
    var onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { _, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}


/**
 * Apple HIG Materials 材质分级体系 (Material Levels)
 *
 * 严格划分浮动功能层 (Liquid Glass) 与内容展示层 (Standard Materials)：
 * - [UltraThin]：超薄微控件（Chip、Icon、小角标）
 * - [Thin]：交互控件（按钮、Switch拇指、Slider拇指）
 * - [Regular]：浮动功能层（TopBar、BottomBar、FAB、Dialog）
 * - [Thick]：内容承载层（Card、Sheet、内容面板，重在保持内容可读性）
 */
enum class LiquidGlassLevel(
    val blurRadius: Dp,
    val lensMin: Dp,
    val lensMax: Dp,
    val depthEffect: Boolean = true,
    val chromaticAberration: Boolean = false,
    val highlightAlpha: Float = 0.35f,
    val shadowRadius: Dp = 6.dp,
    val shadowAlpha: Float = 0.08f,
    val innerShadowRadius: Dp = 3.dp,
    val innerShadowAlpha: Float = 0.08f,
) {
    /**
     * 超薄材质 (UltraThin)：小微控件、标签 Chip、小图标按钮、角标等。
     * 低模糊 + 极小折射 + 微弱阴影，轻盈通透。
     */
    UltraThin(
        blurRadius = 2.dp,
        lensMin = 4.dp,
        lensMax = 8.dp,
        depthEffect = true,
        chromaticAberration = false,
        highlightAlpha = 0.28f,
        shadowRadius = 4.dp,
        shadowAlpha = 0.06f,
        innerShadowRadius = 2.dp,
        innerShadowAlpha = 0.06f,
    ),

    /**
     * 薄质材质 (Thin)：按钮、开关拇指、滑块拇指等核心交互控件。
     * 细腻模糊 + 中等折射 + 色散透镜 + 拟真内阴影与受光高光。
     */
    Thin(
        blurRadius = 3.dp,
        lensMin = 8.dp,
        lensMax = 14.dp,
        depthEffect = true,
        chromaticAberration = true,
        highlightAlpha = 0.40f,
        shadowRadius = 6.dp,
        shadowAlpha = 0.12f,
        innerShadowRadius = 3.dp,
        innerShadowAlpha = 0.10f,
    ),

    /**
     * 常规浮动材质 (Regular)：导航栏、顶栏、FAB、弹窗等浮动功能层。
     * 中等景深模糊 + 大折射 + 柔和环境光与外投射阴影。
     */
    Regular(
        blurRadius = 10.dp,
        lensMin = 14.dp,
        lensMax = 22.dp,
        depthEffect = true,
        chromaticAberration = false,
        highlightAlpha = 0.35f,
        shadowRadius = 10.dp,
        shadowAlpha = 0.10f,
        innerShadowRadius = 4.dp,
        innerShadowAlpha = 0.08f,
    ),

    /**
     * 厚质内容材质 (Thick / Content)：内容卡片 (RuntimeCard)、底层画板。
     * 遵循 Apple HIG "Deference to Content"，平缓模糊 + 低畸变透镜，保证内部文本与代码清晰可读。
     */
    Thick(
        blurRadius = 6.dp,
        lensMin = 12.dp,
        lensMax = 20.dp,
        depthEffect = true,
        chromaticAberration = false,
        highlightAlpha = 0.25f,
        shadowRadius = 8.dp,
        shadowAlpha = 0.08f,
        innerShadowRadius = 4.dp,
        innerShadowAlpha = 0.05f,
    )
}

/**
 * 苹果流体弹簧物理标准配置 (Apple Liquid Spring Spec)
 */
val LiquidSpringSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
val LiquidOffsetSpringSpec = spring<Offset>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = Offset.VisibilityThreshold)

/**
 * 液态玻璃效果预设集合。
 * 严格遵循 Kyant Backdrop 渲染铁律与 Apple HIG Materials 分级体系：
 * 1. 效果顺序：vibrancy() -> blur() -> lens()
 * 2. 变换安全：所有形变必须置于 layerBlock 内，防止背景采样贴图被拉伸
 * 3. 颜色融合：支持 BlendMode.Hue 真实色相染色
 * 4. 玻璃嵌套：支持 exportedBackdrop 传递，阻断 RenderThread 循环崩溃
 */
object GlassEffects {

    /**
     * 基础通用材质修饰符：按分级材质参数应用 drawBackdrop
     */
    fun Modifier.liquidGlassSurface(
        backdrop: Backdrop,
        shape: () -> Shape,
        level: LiquidGlassLevel = LiquidGlassLevel.Regular,
        exportedBackdrop: LayerBackdrop? = null,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
        onDrawSurface: (androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit)? = null,
    ): Modifier = drawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = {
            vibrancy()
            blur(level.blurRadius.toPx())
            lens(
                refractionHeight = level.lensMin.toPx(),
                refractionAmount = level.lensMax.toPx(),
                depthEffect = level.depthEffect,
                chromaticAberration = level.chromaticAberration,
            )
        },
        highlight = if (level.highlightAlpha > 0f) {
            { Highlight.Default.copy(alpha = level.highlightAlpha) }
        } else null,
        shadow = if (level.shadowAlpha > 0f) {
            { Shadow(radius = level.shadowRadius, alpha = level.shadowAlpha) }
        } else null,
        innerShadow = if (level.innerShadowAlpha > 0f) {
            { InnerShadow(radius = level.innerShadowRadius, alpha = level.innerShadowAlpha) }
        } else null,
        exportedBackdrop = exportedBackdrop,
        layerBlock = layerBlock,
        onDrawSurface = onDrawSurface,
    )

    /**
     * 内容卡片级材质（Thick 材质，支持嵌套玻璃 exportedBackdrop 与安全 layerBlock 缩放）
     */
    fun Modifier.glassCard(
        backdrop: Backdrop,
        shape: () -> Shape,
        surfaceColor: Color = Color.White.copy(alpha = 0.28f),
        exportedBackdrop: LayerBackdrop? = null,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    ): Modifier = liquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        level = LiquidGlassLevel.Thick,
        exportedBackdrop = exportedBackdrop,
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(surfaceColor) },
    )

    /**
     * 控件级材质（Thin 材质，适合按钮与核心触控组件）
     */
    fun Modifier.glassControl(
        backdrop: Backdrop,
        shape: () -> Shape,
        tint: Color = Color.Transparent,
        tonal: Boolean = false,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    ): Modifier = liquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        level = LiquidGlassLevel.Thin,
        layerBlock = layerBlock,
        onDrawSurface = if (tint != Color.Transparent) {
            {
                drawRect(tint, blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = if (tonal) 0.35f else 0.70f))
            }
        } else null,
    )

    /**
     * 浮动导航栏/顶栏级材质（Regular 材质）
     */
    fun Modifier.glassBar(
        backdrop: Backdrop,
        shape: () -> Shape,
        surfaceColor: Color = Color.Transparent,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    ): Modifier = liquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        level = LiquidGlassLevel.Regular,
        layerBlock = layerBlock,
        onDrawSurface = if (surfaceColor != Color.Transparent) {
            { drawRect(surfaceColor) }
        } else null,
    )

    /**
     * 微型指示器/焦点水滴材质（色散 + 高受光）
     */
    fun Modifier.glassIndicator(
        backdrop: Backdrop,
        shape: () -> Shape,
        surfaceColor: Color = Color.White.copy(alpha = 0.90f),
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    ): Modifier = liquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        level = LiquidGlassLevel.Thin,
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(surfaceColor) },
    )
}

