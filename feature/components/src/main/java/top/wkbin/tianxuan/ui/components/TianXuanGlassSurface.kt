package top.wkbin.tianxuan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop
import top.wkbin.tianxuan.ui.components.GlassEffects.liquidGlassSurface

/**
 * 天玄高拟真毛玻璃面板 (TianXuan Glass Surface)
 *
 * 1. 深度对齐 Apple HIG Materials 与 Kyant Backdrop 规范：当处于澄明（液态玻璃）主题时，
 *    自动接入 [LocalLiquidGlassSurfaceBackdrop] 渲染真实光学折射与焦散景深；
 * 2. 顶部独占 1px 镜面受光渐变高光 (Specular Highlight)，还原 Apple 玻璃边缘受光特质；
 * 3. 支持胶囊拼接 (omitTopBorder)，当面板紧贴上方组件时省略顶边与高光，杜绝接缝处双重亮线；
 * 4. 非玻璃主题或底层 Backdrop 缺失时，平滑降级为经典细腻微渐变面板。
 */
@Composable
fun TianXuanGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    forceDark: Boolean = false,
    omitTopBorder: Boolean = false,
    showTopHighlight: Boolean = true,
    surfaceColor: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = forceDark || isSystemInDarkTheme()
    val glassBackdrop = LocalLiquidGlassSurfaceBackdrop.current

    val topTint = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }
    val bottomTint = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.25f)
    } else {
        Color.White.copy(alpha = 0.50f)
    }

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    val panelModifier = if (glassBackdrop != null) {
        val glassSurface = surfaceColor ?: if (isDark) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
        } else {
            Color.White.copy(alpha = 0.50f)
        }
        modifier
            .liquidGlassSurface(
                backdrop = glassBackdrop,
                shape = { shape },
                level = LiquidGlassLevel.Thick,
                onDrawSurface = { drawRect(glassSurface) },
            )
            .clip(shape)
            .then(
                if (!omitTopBorder) {
                    Modifier.border(borderWidth, borderColor.copy(alpha = if (isDark) 0.12f else 0.40f), shape)
                } else {
                    Modifier
                }
            )
    } else {
        val backgroundBrush = if (surfaceColor != null) {
            Brush.verticalGradient(listOf(surfaceColor, surfaceColor))
        } else {
            Brush.verticalGradient(listOf(topTint, bottomTint))
        }
        modifier
            .clip(shape)
            .background(backgroundBrush, shape)
            .then(
                if (!omitTopBorder) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            )
    }

    Box(modifier = panelModifier) {
        content()

        // 顶部 1px 镜面高光反射线
        if (showTopHighlight && !omitTopBorder) {
            val highlightBrush = Brush.horizontalGradient(
                listOf(
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = if (isDark) 0.35f else 0.80f),
                    Color.White.copy(alpha = 0.05f),
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(highlightBrush),
            )
        }
    }
}

