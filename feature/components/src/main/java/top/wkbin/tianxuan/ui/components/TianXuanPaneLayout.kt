package top.wkbin.tianxuan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 平板可拖拽分栏的度量与宽度收敛规则。
 *
 * 独立成文件而不是塞进 UI 文件的原因：这些是**纯函数与常量**，是拖拽手感的唯一真源，
 * 必须在没有 Compose 运行环境的情况下也能被完整单测覆盖（Robolectric 跑 Compose 渲染测试很慢，
 * 而宽度收敛的边界值用纯 JUnit 断言更直接、更快）。
 */
object TianXuanPaneMetrics {
    /** 会话栏最小宽度：再窄就放不下「新建会话」按钮与会话标题。 */
    const val SESSIONS_PANE_MIN_DP: Int = 240

    /** 会话栏默认宽度：11 寸平板横屏下的舒适值。 */
    const val SESSIONS_PANE_DEFAULT_DP: Int = 320

    /** 会话栏最大宽度：再宽会话标题反而更长，对话区被挤得没有意义。 */
    const val SESSIONS_PANE_MAX_DP: Int = 460

    /** 主对话区最小宽度：低于此值消息气泡与工具卡片开始截断。 */
    const val MAIN_PANE_MIN_DP: Int = 360

    /** 分隔条视觉宽度。 */
    const val DIVIDER_WIDTH_DP: Int = 8

    /**
     * 分隔条触摸热区宽度：比视觉宽得多。
     *
     * 平板上靠手指精确点中一条 8dp 的细线很难受，热区加宽到 24dp 而视觉不变粗。
     */
    const val DIVIDER_TOUCH_WIDTH_DP: Int = 24

    /** 抽屉模式下的会话栏宽度区间（窄屏沿用抽屉形态）。 */
    const val SESSIONS_DRAWER_MIN_DP: Int = 280
    const val SESSIONS_DRAWER_MAX_DP: Int = 340
}

/** 分隔条的测试标签，供渲染测试定位。 */
const val PANE_DIVIDER_TEST_TAG: String = "tianxuan-pane-divider"

/** 分隔条拖拽中的无障碍描述，供渲染测试与 TalkBack 定位。 */
const val PANE_DIVIDER_DESCRIPTION: String = "调整会话栏宽度"

/** 当前窗口宽度是否够放下「常驻会话栏 + 对话区」两栏。 */
fun canPinSessionsPane(availableWidthDp: Int): Boolean =
    availableWidthDp >= TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP +
        TianXuanPaneMetrics.DIVIDER_WIDTH_DP +
        TianXuanPaneMetrics.MAIN_PANE_MIN_DP

/**
 * 把用户拖出来的会话栏宽度收敛到可用区间。
 *
 * 上界同时受两个约束：[TianXuanPaneMetrics.SESSIONS_PANE_MAX_DP] 与
 * 「主对话区不得被挤到 [TianXuanPaneMetrics.MAIN_PANE_MIN_DP] 以下」。后者才是真正会生效的那个——
 * 窗口变窄时，用户上次记住的 420dp 若直接套用会把对话区压扁。
 *
 * 返回 0 表示窗口已窄到放不下两栏（与 [canPinSessionsPane] 同源，不另立一套判定），
 * 调用方此时不应渲染常驻栏，应改回抽屉形态。
 */
fun resolveSessionsPaneWidth(requestedDp: Int, availableWidthDp: Int): Int {
    if (!canPinSessionsPane(availableWidthDp)) return 0
    val upper = minOf(
        TianXuanPaneMetrics.SESSIONS_PANE_MAX_DP,
        availableWidthDp - TianXuanPaneMetrics.DIVIDER_WIDTH_DP - TianXuanPaneMetrics.MAIN_PANE_MIN_DP,
    )
    // 能常驻时 ceiling 必然 >= MIN；保留下界兜底以防常量日后被改小。
    val lower = minOf(TianXuanPaneMetrics.SESSIONS_PANE_MIN_DP, upper)
    return requestedDp.coerceIn(lower, upper)
}

/**
 * 可拖拽的两栏分隔条。
 *
 * 只负责「把水平拖动的像素增量报出去」，不持有宽度状态——宽度由调用方决定并持久化，
 * 这样分隔条本身可以无状态复用，也便于单测断言回调行为。
 *
 * [onDragDelta] 每帧回调（拖拽中）；[onDragEnd] 在手指抬起或手势被取消时回调一次，
 * 调用方应在这里做「落盘」这类不该每帧做的事。
 */
@Composable
fun RuntimePaneDivider(
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val restingColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .width(TianXuanPaneMetrics.DIVIDER_TOUCH_WIDTH_DP.dp)
            .fillMaxHeight()
            .testTag(PANE_DIVIDER_TEST_TAG)
            .semantics { contentDescription = PANE_DIVIDER_DESCRIPTION }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(TianXuanPaneMetrics.DIVIDER_WIDTH_DP.dp)
                .fillMaxHeight()
                .background(if (dragging) activeColor else restingColor),
        )
        // 拖动把手：视觉上给一个明确的「可拖」锚点，避免用户不知道这条线能拖
        Box(
            modifier = Modifier
                .size(4.dp, 36.dp)
                .background(
                    color = if (dragging) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}