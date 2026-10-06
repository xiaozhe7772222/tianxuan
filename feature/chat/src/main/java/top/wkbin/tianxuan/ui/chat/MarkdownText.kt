package top.wkbin.tianxuan.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Re-export of [top.wkbin.tianxuan.ui.components.MarkdownText] for backward compatibility in feature:chat.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    contentCacheKey: String? = null,
) {
    top.wkbin.tianxuan.ui.components.MarkdownText(
        markdown = markdown,
        modifier = modifier,
        contentCacheKey = contentCacheKey,
    )
}

val LocalSandboxHostRoots get() = top.wkbin.tianxuan.ui.components.LocalSandboxHostRoots

internal fun markdownBlocksPreview(
    markdown: String,
    contentCacheKey: String? = null,
    largeCacheMinChars: Int = 128_000,
): String = top.wkbin.tianxuan.ui.components.markdownBlocksPreview(markdown, contentCacheKey, largeCacheMinChars)

internal fun clearMarkdownCachesForTest() {
    top.wkbin.tianxuan.ui.components.clearMarkdownCachesForTest()
}
