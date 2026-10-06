package top.wkbin.tianxuan.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.RuntimeBottomBar
import top.wkbin.tianxuan.ui.components.RuntimeNavRail
import top.wkbin.tianxuan.ui.components.TianXuanWidthClass
import top.wkbin.tianxuan.ui.components.rememberWidthClass

/**
 * 天玄 · 自适应应用外壳。
 *
 * 手机与平板走两套导航形态，判据是 [TianXuanWidthClass]：
 * - 紧凑（手机竖屏）：底部栏 + 单内容区，由 [bottomBar] 承担导航；
 * - 中等/ 展开（平板）：常驻侧栏 + 内容区并排，底部栏不渲染。
 *
 * 单独成文件而不写在 NavHost 里：NavHost 已有 800+ 行且受行数棘轮约束，
 * 布局分支混进去会持续顶破基线。这里只放外壳，内容由调用方以 lambda 传入。
 *
 * 底部栏由[RetainedBottomBar] 负责叠放，不作为本函数的参数：
 * 它需要 BoxScope 的 align(BottomCenter)，而宽屏分支内部是 Row 作用域。
 */
@Composable
fun AdaptiveAppShell(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    widthClass: TianXuanWidthClass = rememberWidthClass(),
    /** 内容区。传入的 modifier 已按形态预置好尺寸，勿再fillMaxSize 覆盖 */
    content: @Composable (Modifier) -> Unit,
) {
    if (widthClass.usesPermanentNav) {
        Row(modifier = modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxHeight(),
            ) {
                RuntimeNavRail(
                    selected = selected,
                    onNavigate = onNavigate,
                    widthClass = widthClass,
                )
            }
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        content(Modifier.fillMaxSize())
    }
}

/**
 * 完整外壳：自适应导航 + 可选常驻液态玻璃底栏。
 *
 * 底栏须「常驻组合」而非按需显示：重建毛玻璃折射层是返回导航卡顿的主因。
 * 故用 zIndex/alpha/位移把它移出屏幕而不是移出组合——
 * 但移出屏幕后它仍会拦截点击，所以 alpha=0 时 zIndex 同步降为 -1。
 *
 * 宽屏（常驻侧栏）下整个底栏不渲染：侧栏已承担导航，再放底栏会重复。
 */
@Composable
fun AdaptiveNavigationScaffold(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    widthClass: TianXuanWidthClass = rememberWidthClass(),
    hasLiquidBackdrop: Boolean,
    /** 底栏是否真正可见：次级页面打开、键盘弹出时收起 */
    bottomBarVisible: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val usePermanentNav = widthClass.usesPermanentNav
    Box(modifier = modifier.fillMaxSize()) {
        AdaptiveAppShell(
            selected = selected,
            onNavigate = onNavigate,
            widthClass = widthClass,
            content = content,
        )
        if (hasLiquidBackdrop && !usePermanentNav) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(if (bottomBarVisible) 1f else -1f)
                    .graphicsLayer {
                        alpha = if (bottomBarVisible) 1f else 0f
                        translationY = if (bottomBarVisible) 0f else size.height
                    },
            ) {
                RuntimeBottomBar(selected = selected, onNavigate = onNavigate)
            }
        }
    }
}