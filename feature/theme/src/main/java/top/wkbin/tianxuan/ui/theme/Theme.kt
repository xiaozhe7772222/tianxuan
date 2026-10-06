package top.wkbin.tianxuan.ui.theme

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.feature.theme.R

/**
 * 天玄内置主题风格。
 *
 * - [XUANTONG]（玄同）—— 默认主题。源自《老子》「万物同归于玄」，Material You 设计系统（支持 Monet 动态壁纸取色与曜石/素白基调）。
 * - [CHENGMING]（澄明）—— 液态玻璃主题。源自「澄明」通透清澈之意，以毛玻璃折射 + 流光 Aurora 渲染虚实交织的界面。
 */
enum class ThemeStyle(
    val id: String,
    val displayName: String,
    val displayNameEn: String,
    val description: String,
) {
    MATERIAL_YOU(
        id = "material_you",
        displayName = "Material You",
        displayNameEn = "Material You",
        description = "默认主题 · Google Material You (M3) 原生设计系统，支持跟随系统壁纸动态取色与经典曜石配色。",
    ),
    LIQUID_GLASS(
        id = "liquid_glass",
        displayName = "液态玻璃",
        displayNameEn = "Liquid Glass",
        description = "液态玻璃主题 · 毛玻璃折射 + 流光 Aurora，通透清澈的玻璃质感。",
    ),
    ;

    companion object {
        @Deprecated("使用 MATERIAL_YOU 代替", ReplaceWith("MATERIAL_YOU"))
        val XUANTONG get() = MATERIAL_YOU
        @Deprecated("使用 LIQUID_GLASS 代替", ReplaceWith("LIQUID_GLASS"))
        val CHENGMING get() = LIQUID_GLASS

        fun fromId(id: String?): ThemeStyle = when (id) {
            "liquid_glass", "chengming" -> LIQUID_GLASS
            "material_you", "xuantong" -> MATERIAL_YOU
            else -> MATERIAL_YOU
        }
    }
}

// ======================= 「玄同 · Xuantong」= 默认 M3 Expressive 配色 =======================

val XuantongLightColors = lightColorScheme(
    primary = M3ExpLightPrimary,
    onPrimary = M3ExpLightOnPrimary,
    primaryContainer = M3ExpLightPrimaryContainer,
    onPrimaryContainer = M3ExpLightOnPrimaryContainer,
    secondary = M3ExpLightSecondary,
    onSecondary = M3ExpLightOnSecondary,
    secondaryContainer = M3ExpLightSecondaryContainer,
    onSecondaryContainer = M3ExpLightOnSecondaryContainer,
    tertiary = M3ExpLightTertiary,
    onTertiary = M3ExpLightOnTertiary,
    tertiaryContainer = M3ExpLightTertiaryContainer,
    onTertiaryContainer = M3ExpLightOnTertiaryContainer,
    error = M3ExpLightError,
    onError = Color.White,
    errorContainer = M3ExpLightErrorContainer,
    onErrorContainer = Color(0xFF410002),
    background = M3ExpLightBackground,
    onBackground = M3ExpLightOnBackground,
    surface = M3ExpLightSurface,
    onSurface = M3ExpLightOnSurface,
    surfaceVariant = M3ExpLightSurfaceVariant,
    onSurfaceVariant = M3ExpLightOnSurfaceVariant,
    surfaceContainerLowest = M3ExpLightSurfaceContainerLowest,
    surfaceContainerLow = M3ExpLightSurfaceContainerLow,
    surfaceContainer = M3ExpLightSurfaceContainer,
    surfaceContainerHigh = M3ExpLightSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpLightSurfaceContainerHighest,
    outline = M3ExpLightOutline,
    outlineVariant = M3ExpLightOutlineVariant,
)

val XuantongDarkColors = darkColorScheme(
    primary = M3ExpDarkPrimary,
    onPrimary = M3ExpDarkOnPrimary,
    primaryContainer = M3ExpDarkPrimaryContainer,
    onPrimaryContainer = M3ExpDarkOnPrimaryContainer,
    secondary = M3ExpDarkSecondary,
    onSecondary = M3ExpDarkOnSecondary,
    secondaryContainer = M3ExpDarkSecondaryContainer,
    onSecondaryContainer = M3ExpDarkOnSecondaryContainer,
    tertiary = M3ExpDarkTertiary,
    onTertiary = M3ExpDarkOnTertiary,
    tertiaryContainer = M3ExpDarkTertiaryContainer,
    onTertiaryContainer = M3ExpDarkOnTertiaryContainer,
    error = M3ExpDarkError,
    onError = Color(0xFF690005),
    errorContainer = M3ExpDarkErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6),
    background = M3ExpDarkBackground,
    onBackground = M3ExpDarkOnBackground,
    surface = M3ExpDarkSurface,
    onSurface = M3ExpDarkOnSurface,
    surfaceVariant = M3ExpDarkSurfaceVariant,
    onSurfaceVariant = M3ExpDarkOnSurfaceVariant,
    surfaceContainerLowest = M3ExpDarkSurfaceContainerLowest,
    surfaceContainerLow = M3ExpDarkSurfaceContainerLow,
    surfaceContainer = M3ExpDarkSurfaceContainer,
    surfaceContainerHigh = M3ExpDarkSurfaceContainerHigh,
    surfaceContainerHighest = M3ExpDarkSurfaceContainerHighest,
    outline = M3ExpDarkOutline,
    outlineVariant = M3ExpDarkOutlineVariant,
)

// ======================= 「澄明 · Chengming」液态玻璃配色 =======================

val ChengmingLightColors = lightColorScheme(
    primary = ChengmingLightPrimary,
    onPrimary = ChengmingLightOnPrimary,
    primaryContainer = ChengmingLightPrimaryContainer,
    onPrimaryContainer = ChengmingLightOnPrimaryContainer,
    secondary = ChengmingLightSecondary,
    onSecondary = ChengmingLightOnSecondary,
    secondaryContainer = ChengmingLightSecondaryContainer,
    onSecondaryContainer = ChengmingLightOnSecondaryContainer,
    tertiary = ChengmingLightTertiary,
    onTertiary = ChengmingLightOnTertiary,
    tertiaryContainer = ChengmingLightTertiaryContainer,
    onTertiaryContainer = ChengmingLightOnTertiaryContainer,
    error = ChengmingLightError,
    onError = ChengmingLightOnError,
    errorContainer = ChengmingLightErrorContainer,
    onErrorContainer = ChengmingLightOnErrorContainer,
    background = ChengmingLightBackground,
    onBackground = ChengmingLightOnBackground,
    surface = ChengmingLightSurface,
    onSurface = ChengmingLightOnSurface,
    surfaceVariant = ChengmingLightSurfaceVariant,
    onSurfaceVariant = ChengmingLightOnSurfaceVariant,
    surfaceContainerLowest = ChengmingLightSurfaceContainerLowest,
    surfaceContainerLow = ChengmingLightSurfaceContainerLow,
    surfaceContainer = ChengmingLightSurfaceContainer,
    surfaceContainerHigh = ChengmingLightSurfaceContainerHigh,
    surfaceContainerHighest = ChengmingLightSurfaceContainerHighest,
    outline = ChengmingLightOutline,
    outlineVariant = ChengmingLightOutlineVariant,
)

val ChengmingDarkColors = darkColorScheme(
    primary = ChengmingDarkPrimary,
    onPrimary = ChengmingDarkOnPrimary,
    primaryContainer = ChengmingDarkPrimaryContainer,
    onPrimaryContainer = ChengmingDarkOnPrimaryContainer,
    secondary = ChengmingDarkSecondary,
    onSecondary = ChengmingDarkOnSecondary,
    secondaryContainer = ChengmingDarkSecondaryContainer,
    onSecondaryContainer = ChengmingDarkOnSecondaryContainer,
    tertiary = ChengmingDarkTertiary,
    onTertiary = ChengmingDarkOnTertiary,
    tertiaryContainer = ChengmingDarkTertiaryContainer,
    onTertiaryContainer = ChengmingDarkOnTertiaryContainer,
    error = ChengmingDarkError,
    onError = ChengmingDarkOnError,
    errorContainer = ChengmingDarkErrorContainer,
    onErrorContainer = ChengmingDarkOnErrorContainer,
    background = ChengmingDarkBackground,
    onBackground = ChengmingDarkOnBackground,
    surface = ChengmingDarkSurface,
    onSurface = ChengmingDarkOnSurface,
    surfaceVariant = ChengmingDarkSurfaceVariant,
    onSurfaceVariant = ChengmingDarkOnSurfaceVariant,
    surfaceContainerLowest = ChengmingDarkSurfaceContainerLowest,
    surfaceContainerLow = ChengmingDarkSurfaceContainerLow,
    surfaceContainer = ChengmingDarkSurfaceContainer,
    surfaceContainerHigh = ChengmingDarkSurfaceContainerHigh,
    surfaceContainerHighest = ChengmingDarkSurfaceContainerHighest,
    outline = ChengmingDarkOutline,
    outlineVariant = ChengmingDarkOutlineVariant,
)

/**
 * Material 3 形状体系 (Shape Scale)
 */
val TianXuanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TianXuanTheme(
    style: ThemeStyle = ThemeStyle.MATERIAL_YOU,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    backgroundUri: String? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.isNavigationBarContrastEnforced = false
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val context = LocalContext.current
    val staticFallback = if (darkTheme) XuantongDarkColors else XuantongLightColors
    val systemMonet = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        staticFallback
    }

    val colorScheme = when (style) {
        ThemeStyle.MATERIAL_YOU -> {
            if (dynamicColor) {
                rememberWallpaperDynamicColorScheme(
                    darkTheme = darkTheme,
                    fallback = systemMonet,
                )
            } else {
                staticFallback
            }
        }
        ThemeStyle.LIQUID_GLASS -> if (darkTheme) ChengmingDarkColors else ChengmingLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (style == ThemeStyle.LIQUID_GLASS) LiquidGlassTypography else AppTypography,
        shapes = TianXuanShapes,
    ) {
        if (style == ThemeStyle.LIQUID_GLASS) {
            LiquidGlassRoot(content, darkTheme, backgroundUri)
        } else {
            content()
        }
    }
}

// ======================= 澄明 · 液态玻璃根容器 =======================

/**
 * 当前液态玻璃折射源（[LayerBackdrop]），仅在澄明主题存在（否则为 null）。
 * 底部导航等"液态玻璃"组件可通过 [LocalLiquidGlassBackdrop] 读取并叠加毛玻璃折射。
 */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 澄明组件使用的稳定背景折射源。
 *
 * 页面内容会记录到 [LocalLiquidGlassBackdrop] 供底部导航折射；卡片、按钮等页面内部组件
 * 不能反过来读取同一记录层，否则部分设备的 RenderThread 会形成递归合成。这里单独记录
 * Aurora 根背景，让任意层级的玻璃组件都能安全取样。
 */
val LocalLiquidGlassSurfaceBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 液态玻璃根容器（核心面液态玻璃）：
 * 底层绘制缓慢漂移的流光 Aurora，并通过 [layerBackdrop] 捕获为折射源；
 * 应用内容绘制在其上，半透明配色透出流光。底部导航等玻璃组件读取
 * [LocalLiquidGlassBackdrop]，用 [com.kyant.backdrop.drawBackdrop] 叠加毛玻璃折射，
 * 呈现悬浮磨砂玻璃效果（见 RuntimeBottomBar）。
 *
 * 在 RenderEffect 不可用的设备上，折射会自动退化为"半透明冰霜 + 流光底色"，不崩溃。
 */
@Composable
private fun LiquidGlassRoot(content: @Composable () -> Unit, darkTheme: Boolean, backgroundUri: String?) {
    val pageBackdrop = rememberLayerBackdrop()
    val surfaceBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides pageBackdrop,
        LocalLiquidGlassSurfaceBackdrop provides surfaceBackdrop,
    ) {
        // Keep the source layer separate from consumers of the backdrop. Capturing a
        // parent that also contains drawBackdrop surfaces can recurse inside RenderThread.
        Box(modifier = Modifier.fillMaxSize()) {
            ChengmingBackdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(surfaceBackdrop),
                darkTheme = darkTheme,
                backgroundUri = backgroundUri,
            )
            content()
        }
    }
}

/**
 * 澄明的折射背景。用户选择了自定义壁纸时以壁纸为折射源（高信息量图像让玻璃边缘
 * 呈现清晰的位移、亮度与色彩变化）；未选择时不使用任何壁纸，仅铺主题基调纯色底，
 * 保证半透明配色在无壁纸下依然可读。
 */
@Composable
private fun ChengmingBackdrop(modifier: Modifier, darkTheme: Boolean, backgroundUri: String?) {
    val context = LocalContext.current
    val customPainter by produceState<BitmapPainter?>(
        initialValue = null,
        key1 = context,
        key2 = backgroundUri,
    ) {
        value = withContext(Dispatchers.IO) {
            backgroundUri?.takeIf { it.isNotBlank() }?.let { savedUri ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(savedUri))?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()?.let(::BitmapPainter)
                    }
                }.getOrNull()
            }
        }
    }

    val wallpaper = customPainter
    Box(modifier) {
        if (wallpaper != null) {
            Image(
                painter = wallpaper,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 深浅色自适应蒙版：浅色保持纯净晶莹，深色微暗化营造静谧夜色玻璃感并强化文字对比度
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (darkTheme) {
                            Color(0x99060B14)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        },
                    ),
            )
        } else {
            // 无自定义壁纸：不使用默认壁纸，铺澄明基调纯色底
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (darkTheme) ChengmingNoWallpaperDarkBase else ChengmingNoWallpaperLightBase),
            )
        }
    }
}
