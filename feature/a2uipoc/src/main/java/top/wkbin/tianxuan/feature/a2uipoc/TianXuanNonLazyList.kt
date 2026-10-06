package top.wkbin.tianxuan.feature.a2uipoc

import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 宿主自实现的 A2UI Basic Catalog「List」组件（非懒加载版）。
 *
 * 背景（为什么必须覆写）：
 * 官方 [androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1List] 在
 * direction=Vertical 时使用 LazyColumn、Horizontal 时使用 LazyRow。而 render_surface
 * 生成的界面是「内嵌在聊天流的 LazyColumn 项里」渲染的，聊天项在纵向上拿到的约束是
 * maxHeight = Infinity。垂直懒列表一旦测得无限高约束，foundation 会直接断言失败：
 *   androidx.compose.foundation -> checkScrollableContainerConstraints()
 *   IllegalStateException("Vertically scrollable component was measured with an
 *   infinity maximum height constraints ...")
 * 异常发生在 measure 阶段且无捕获点，进程被杀（用户表现为「滑到那张卡就闪退」）。
 *
 * 本实现用普通 Column/Row 逐项展开，不做内部滚动，因此可以安全地内嵌进任何
 * 纵向滚动列表（聊天流、设置页等）。代价是超长列表不再懒加载——对聊天内嵌的
 * PoC 场景可接受；若将来需要长列表，应改为「有界高度容器 + LazyColumn」。
 *
 * 接入方式（TianXuanA2uiRenderer.kt）：
 *   private val catalog = materialA2uiBasicCatalogV1(
 *       image = TianXuanImageComponent(),
 *       video = TianXuanVideoComponent(),
 *       audioPlayer = TianXuanAudioPlayerComponent(),
 *       urlOpener = TianXuanUrlOpener,
 *       list = TianXuanNonLazyList,          // <<< 新增这一行
 *   )
 */
internal object TianXuanNonLazyList : A2uiBasicCatalogV1.List {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        when (direction) {
            A2uiBasicCatalogV1.List.Direction.Horizontal -> {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = align.toVerticalAlignment(),
                ) {
                    children.forEach { childRef ->
                        ListItem(childRef = childRef, modifier = Modifier)
                    }
                }
            }

            A2uiBasicCatalogV1.List.Direction.Vertical -> {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = align.toHorizontalAlignment(),
                ) {
                    children.forEach { childRef ->
                        ListItem(childRef = childRef, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // 说明：官方实现还会调用 modifier.a2uiAccessibility(accessibility)，
        // 该扩展函数是 material3-a2ui 模块的 internal，宿主模块无法调用；
        // 如需无障碍语义，可自行 Modifier.semantics { contentDescription = ... }。
    }

    @Composable
    private fun A2uiComponentScope.ListItem(
        childRef: A2uiComponentReference,
        modifier: Modifier = Modifier,
    ) {
        // 与官方 ListItemStateWrapper 等价：按子组件状态 加载中/失败/成功 三态渲染，
        // 去掉 AnimatedContent 过渡，避免依赖 MaterialA2uiDefaults 的内部成员。
        when (val state = observeA2uiComponentState(childRef)) {
            is A2uiComponentState.Success ->
                A2uiComponent(component = state.component, modifier = modifier)

            is A2uiComponentState.Error ->
                Text(
                    text = "子组件渲染失败：" + (state.exception.message ?: "未知错误"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = modifier,
                )

            is A2uiComponentState.Loading ->
                Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = modifier,
                )
        }
    }
}

private fun A2uiBasicCatalogV1.List.Align.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        A2uiBasicCatalogV1.List.Align.Start -> Alignment.Start
        A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterHorizontally
        A2uiBasicCatalogV1.List.Align.End -> Alignment.End
        A2uiBasicCatalogV1.List.Align.Stretch -> Alignment.Start
    }

private fun A2uiBasicCatalogV1.List.Align.toVerticalAlignment(): Alignment.Vertical =
    when (this) {
        A2uiBasicCatalogV1.List.Align.Start -> Alignment.Top
        A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterVertically
        A2uiBasicCatalogV1.List.Align.End -> Alignment.Bottom
        A2uiBasicCatalogV1.List.Align.Stretch -> Alignment.Top
    }
