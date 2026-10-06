package top.wkbin.tianxuan.feature.a2uipoc

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.AccessibilityAttributes
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * render_surface PoC 的自实现目录件：官方 Basic Catalog 把「媒体渲染」与
 * 「客户端环境函数」留给宿主实现，这里给最小占位实现——
 * - Image/Video/AudioPlayer：占位卡片（正式实现可接 Coil / ExoPlayer）；
 * - UrlOpener：PoC 阶段不打开外部链接；
 * - MessageFormatter：原样返回模板（正式实现可接 ICU MessageFormat）。
 * LocaleProvider 使用官方 A2uiLocaleProvider.Default。
 */

internal class TianXuanImageComponent : A2uiBasicCatalogV1.Image {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        url: String,
        description: String?,
        fit: A2uiBasicCatalogV1.Image.Fit,
        variant: A2uiBasicCatalogV1.Image.Variant,
        accessibility: AccessibilityAttributes?,
        modifier: Modifier,
    ) = MediaPlaceholder("🖼 图片：${description.orEmpty().ifBlank { url }}", modifier)
}

internal class TianXuanVideoComponent : A2uiBasicCatalogV1.Video {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        url: String,
        accessibility: AccessibilityAttributes?,
        modifier: Modifier,
    ) = MediaPlaceholder("🎬 视频：$url", modifier)
}

internal class TianXuanAudioPlayerComponent : A2uiBasicCatalogV1.AudioPlayer {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        url: String,
        description: String?,
        accessibility: AccessibilityAttributes?,
        modifier: Modifier,
    ) = MediaPlaceholder("🎧 音频：${description.orEmpty().ifBlank { url }}", modifier)
}

internal object TianXuanUrlOpener : A2uiUrlOpener {
    override fun openUrl(url: String) {
        // PoC：不打开外部链接，避免未审计的 URL 直接离开应用
    }
}

internal object TianXuanMessageFormatter : A2uiMessageFormatter {
    override fun format(pattern: String, locale: Locale, arguments: Map<String, Any>): String = pattern
}

@Composable
private fun MediaPlaceholder(label: String, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
