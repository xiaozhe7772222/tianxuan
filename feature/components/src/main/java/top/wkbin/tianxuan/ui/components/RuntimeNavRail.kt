package top.wkbin.tianxuan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 天玄 · 宽屏常驻导航侧栏。
 *
 * 手机形态用底部栏（见 [RuntimeBottomBar]），平板横屏下换成常驻侧栏：
 * 拇指够不到底栏、键盘弹出后底栏被顶掉、且侧栏能一直显示当前门的位置——
 * 这三点是平板端的核心痛点。
 *
 * 宽度取自 [LocalConfiguration] 而非 Activity 的 windowMetrics：Compose 侧
 * 拿到的 configuration 已扣除系统栏，且在折叠/分屏时会随窗口变化重组，
 * 不需要额外注册监听。用 remember 缓存避免每次重组重复计算。
 */
@Composable
fun rememberWidthClass(): TianXuanWidthClass {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    // 屏幕宽度只在配置变化时才变，按它做 key 记住结果，避免无关重组重复判定
    return remember(widthDp) { widthClassOf(widthDp) }
}

/**
 * 常驻导航侧栏。
 *
 * @param selected 当前选中的门
 * @param onNavigate 点击导航
 * @param widthClass 宽度分型，由调用方决定（便于在测试中固定注入）
 * @param modifier 布局修饰
 */
@Composable
fun RuntimeNavRail(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    widthClass: TianXuanWidthClass = rememberWidthClass(),
) {
    val railWidth: Dp = if (widthClass.showsNavLabels) {
        TianXuanBreakpoints.RAIL_WIDTH_DP.dp
    } else {
        TianXuanBreakpoints.COMPACT_RAIL_WIDTH_DP.dp
    }

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MainDestination.entries.forEach { destination ->
            NavRailItem(
                destination = destination,
                selected = selected == destination,
                showLabel = widthClass.showsNavLabels,
                onClick = { onNavigate(destination) },
            )
        }
    }
}

/**
 * 单个导航项。
 *
 * 选中态用容器底色 + 文字加粗，而非只靠图标颜色变化：
 * 平板上导航项距拇指远，需要更明确的选中提示。
 */
@Composable
private fun NavRailItem(
    destination: MainDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(destination.labelRes)
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // testTag 供自适应外壳的渲染测试定位。
            // 不用中文文案做断言：星象体系改名会让测试因无关原因失败。
            .testTag(NAV_ITEM_TEST_TAG)
            // 容器底色承担「选中」的可视提示：平板上导航项离拇指远，
            // 只靠图标颜色变化不足以让人一眼看出当前在哪一门。
            .background(container, MaterialTheme.shapes.large)
            .padding(vertical = 10.dp, horizontal = if (showLabel) 12.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RuntimeIconButton(
            onClick = onClick,
            contentDescription = label,
        ) {
            RuntimeIcon(name = destination.icon, tint = content)
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 导航项的语义标记，供自适应外壳渲染测试定位节点。 */
const val NAV_ITEM_TEST_TAG: String = "adaptive-nav-item"
