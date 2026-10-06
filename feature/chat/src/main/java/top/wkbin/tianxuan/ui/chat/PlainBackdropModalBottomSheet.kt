package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop

/**
 * Glass-on-Glass 铁律的裸 M3 ModalBottomSheet 适配层。
 *
 * M3 ModalBottomSheet 承载在独立 dialog 窗口：澄明主题下全局注入的
 * [LocalLiquidGlassSurfaceBackdrop]（活动窗口的 Aurora 背景记录层）在 sheet 内容里
 * 依然可见，RuntimeCard / RuntimeIconButton 等玻璃组件的 drawBackdrop 会跨窗口
 * 逐帧采样主窗口图层——滚动 sheet 内容时部分设备表现为疯狂闪烁（RenderThread
 * 反复合成失步）。主题文档对此早有警告：页面内部组件不得反向读取同一记录层。
 *
 * 这里对 sheet 内容统一置空 backdrop，让所有玻璃组件回退普通材质路径。
 * 需要真实玻璃效果的 sheet 应改用 RuntimeModalBottomSheet（创建专属
 * sheetBackdrop 注入，见 RuntimeGlassControls），而不是重新接回全局层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlainBackdropModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalLiquidGlassSurfaceBackdrop provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            content = content,
        )
    }
}
