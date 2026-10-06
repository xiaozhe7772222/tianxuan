package top.wkbin.tianxuan.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内存全局缓存的壁纸种子色，避免 Composable 重构时发生闪烁
 */
@Volatile
private var cachedWallpaperSeed: Color? = null

/**
 * 从系统中提取当前壁纸的主色调种子（Seed Color）。
 *
 * 容灾策略：
 * 1. 优先从 [WallpaperManager.getDrawable] 获取真实壁纸位图并采样分析（彻底规避 Vivo OriginOS / 小米 HyperOS 等定制 ROM 将系统动态色锁死为静态 preset 的问题）；
 * 2. 若 Drawable 受限无法读取，回退至 [WallpaperManager.getWallpaperColors]（API 27+）；
 * 3. 发生任何异常时安全返回 null。
 */
fun extractWallpaperSeedColor(context: Context): Color? {
    val wallpaperManager = runCatching { WallpaperManager.getInstance(context) }.getOrNull() ?: return null

    // 1. 优先尝试从真实壁纸 Drawable 获取（天玄已具 MANAGE_EXTERNAL_STORAGE 特权）
    val colorFromDrawable = runCatching {
        val drawable = wallpaperManager.drawable
        if (drawable != null) {
            extractColorFromDrawable(drawable)
        } else {
            null
        }
    }.getOrNull()

    if (colorFromDrawable != null) {
        cachedWallpaperSeed = colorFromDrawable
        return colorFromDrawable
    }

    // 2. 备用：从系统 WallpaperColors 提取
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val sysColors = runCatching {
            wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()
        val primary = sysColors?.primaryColor
        if (primary != null) {
            val color = Color(primary.toArgb())
            if (color != Color.Transparent && color.alpha > 0.5f) {
                cachedWallpaperSeed = color
                return color
            }
        }
    }

    return cachedWallpaperSeed
}

/**
 * 将壁纸 Drawable 降采样绘制为 64x64 位图，并提取最具表现力的主色调
 */
private fun extractColorFromDrawable(drawable: Drawable): Color? {
    val targetWidth = 64
    val targetHeight = 64
    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, targetWidth, targetHeight)
    drawable.draw(canvas)

    // A. 优先利用 Android 系统官方算法分析位图
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        val wpColors = runCatching { WallpaperColors.fromBitmap(bitmap) }.getOrNull()
        val primary = wpColors?.primaryColor
        if (primary != null) {
            val c = Color(primary.toArgb())
            if (c != Color.Transparent && c.alpha > 0.5f) {
                bitmap.recycle()
                return c
            }
        }
    }

    // B. 容灾：遍历位图像素直方图，寻找饱和度适中、色相明确的主色
    val pixels = IntArray(targetWidth * targetHeight)
    bitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
    bitmap.recycle()

    val hsl = FloatArray(3)
    var bestColor = 0
    var maxScore = -1f

    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xff
        if (alpha < 128) continue
        ColorUtils.colorToHSL(pixel, hsl)
        val s = hsl[1]
        val l = hsl[2]
        // 过滤极黑与极白
        if (l < 0.10f || l > 0.95f) continue
        // 评分：彩度鲜明、明度舒适的像素得分高
        val score = s * (1f - kotlin.math.abs(l - 0.5f) * 1.5f).coerceAtLeast(0.1f)
        if (score > maxScore) {
            maxScore = score
            bestColor = pixel
        }
    }

    return if (bestColor != 0) Color(bestColor) else null
}

/**
 * 根据种子色严格按照 Google Material 3 调色板规范派生完整的深浅色 [ColorScheme]
 */
fun generateM3ColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val seedArgb = seedColor.toArgb()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb, hsl)
    val h = hsl[0] // 0f .. 360f
    val s = hsl[1] // 0f .. 1f

    // 若种子色几乎无彩度（如纯黑白灰壁纸），给一个基础色相与柔和彩度保证界面不枯燥
    val effectiveS = if (s < 0.05f) 0.15f else s.coerceIn(0.20f, 0.70f)
    val effectiveH = if (s < 0.05f) 220f else h

    val primaryH = effectiveH
    val primaryS = effectiveS

    val secondaryH = effectiveH
    val secondaryS = (effectiveS * 0.35f).coerceIn(0.10f, 0.30f)

    val tertiaryH = (effectiveH + 60f) % 360f
    val tertiaryS = (effectiveS * 0.55f).coerceIn(0.18f, 0.50f)

    val neutralS = (effectiveS * 0.06f).coerceAtMost(0.04f)
    val neutralVariantS = (effectiveS * 0.15f).coerceAtMost(0.08f)

    fun hslColor(hue: Float, sat: Float, lum: Float): Color {
        val clampedH = (hue % 360f + 360f) % 360f
        val clampedS = sat.coerceIn(0f, 1f)
        val clampedL = lum.coerceIn(0f, 1f)
        return Color(ColorUtils.HSLToColor(floatArrayOf(clampedH, clampedS, clampedL)))
    }

    return if (!darkTheme) {
        lightColorScheme(
            primary = hslColor(primaryH, primaryS, 0.40f),
            onPrimary = Color.White,
            primaryContainer = hslColor(primaryH, primaryS, 0.90f),
            onPrimaryContainer = hslColor(primaryH, primaryS, 0.10f),
            inversePrimary = hslColor(primaryH, primaryS, 0.80f),

            secondary = hslColor(secondaryH, secondaryS, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hslColor(secondaryH, secondaryS, 0.90f),
            onSecondaryContainer = hslColor(secondaryH, secondaryS, 0.10f),

            tertiary = hslColor(tertiaryH, tertiaryS, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hslColor(tertiaryH, tertiaryS, 0.90f),
            onTertiaryContainer = hslColor(tertiaryH, tertiaryS, 0.10f),

            background = hslColor(primaryH, neutralS, 0.98f),
            onBackground = hslColor(primaryH, neutralS, 0.10f),
            surface = hslColor(primaryH, neutralS, 0.98f),
            onSurface = hslColor(primaryH, neutralS, 0.10f),
            surfaceVariant = hslColor(primaryH, neutralVariantS, 0.90f),
            onSurfaceVariant = hslColor(primaryH, neutralVariantS, 0.30f),
            surfaceTint = hslColor(primaryH, primaryS, 0.40f),
            inverseSurface = hslColor(primaryH, neutralS, 0.20f),
            inverseOnSurface = hslColor(primaryH, neutralS, 0.95f),

            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),

            outline = hslColor(primaryH, neutralVariantS, 0.50f),
            outlineVariant = hslColor(primaryH, neutralVariantS, 0.80f),
            scrim = Color.Black,

            surfaceBright = hslColor(primaryH, neutralS, 0.98f),
            surfaceDim = hslColor(primaryH, neutralS, 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hslColor(primaryH, neutralS, 0.96f),
            surfaceContainer = hslColor(primaryH, neutralS, 0.94f),
            surfaceContainerHigh = hslColor(primaryH, neutralS, 0.92f),
            surfaceContainerHighest = hslColor(primaryH, neutralS, 0.90f),
        )
    } else {
        darkColorScheme(
            primary = hslColor(primaryH, primaryS, 0.80f),
            onPrimary = hslColor(primaryH, primaryS, 0.20f),
            primaryContainer = hslColor(primaryH, primaryS, 0.30f),
            onPrimaryContainer = hslColor(primaryH, primaryS, 0.90f),
            inversePrimary = hslColor(primaryH, primaryS, 0.40f),

            secondary = hslColor(secondaryH, secondaryS, 0.80f),
            onSecondary = hslColor(secondaryH, secondaryS, 0.20f),
            secondaryContainer = hslColor(secondaryH, secondaryS, 0.30f),
            onSecondaryContainer = hslColor(secondaryH, secondaryS, 0.90f),

            tertiary = hslColor(tertiaryH, tertiaryS, 0.80f),
            onTertiary = hslColor(tertiaryH, tertiaryS, 0.20f),
            tertiaryContainer = hslColor(tertiaryH, tertiaryS, 0.30f),
            onTertiaryContainer = hslColor(tertiaryH, tertiaryS, 0.90f),

            background = hslColor(primaryH, neutralS, 0.06f),
            onBackground = hslColor(primaryH, neutralS, 0.90f),
            surface = hslColor(primaryH, neutralS, 0.06f),
            onSurface = hslColor(primaryH, neutralS, 0.90f),
            surfaceVariant = hslColor(primaryH, neutralVariantS, 0.30f),
            onSurfaceVariant = hslColor(primaryH, neutralVariantS, 0.80f),
            surfaceTint = hslColor(primaryH, primaryS, 0.80f),
            inverseSurface = hslColor(primaryH, neutralS, 0.90f),
            inverseOnSurface = hslColor(primaryH, neutralS, 0.20f),

            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),

            outline = hslColor(primaryH, neutralVariantS, 0.60f),
            outlineVariant = hslColor(primaryH, neutralVariantS, 0.30f),
            scrim = Color.Black,

            surfaceBright = hslColor(primaryH, neutralS, 0.24f),
            surfaceDim = hslColor(primaryH, neutralS, 0.06f),
            surfaceContainerLowest = hslColor(primaryH, neutralS, 0.04f),
            surfaceContainerLow = hslColor(primaryH, neutralS, 0.10f),
            surfaceContainer = hslColor(primaryH, neutralS, 0.12f),
            surfaceContainerHigh = hslColor(primaryH, neutralS, 0.17f),
            surfaceContainerHighest = hslColor(primaryH, neutralS, 0.22f),
        )
    }
}

/**
 * 监听系统壁纸变动并提供动态派生的 Material 3 色板，带容灾回退
 */
@Composable
fun rememberWallpaperDynamicColorScheme(
    darkTheme: Boolean,
    fallback: ColorScheme,
): ColorScheme {
    val context = LocalContext.current.applicationContext
    var seedColor by remember { mutableStateOf(cachedWallpaperSeed) }

    DisposableEffect(context) {
        var isDisposed = false
        fun updateSeed() {
            CoroutineScope(Dispatchers.IO).launch {
                val extracted = extractWallpaperSeedColor(context)
                if (!isDisposed && extracted != null && extracted != seedColor) {
                    withContext(Dispatchers.Main) {
                        seedColor = extracted
                    }
                }
            }
        }

        // 初次加载确保刷新
        updateSeed()

        // 1. WallpaperManager colors changed listener
        val colorsListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                    updateSeed()
                }
            }
            val wallpaperManager = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
            wallpaperManager?.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            listener
        } else null

        // 壁纸变化经 WallpaperManager.OnColorsChangedListener 感知（O_MR1+ 覆盖全部 minSdk）；
        // ACTION_WALLPAPER_CHANGED 广播已废弃且不再向三方应用派发，不再注册冗余接收器。

        onDispose {
            isDisposed = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colorsListener != null) {
                runCatching {
                    WallpaperManager.getInstance(context).removeOnColorsChangedListener(colorsListener)
                }
            }
        }
    }

    val currentSeed = seedColor
    return remember(currentSeed, darkTheme, fallback) {
        if (currentSeed != null) {
            generateM3ColorScheme(currentSeed, darkTheme)
        } else {
            fallback
        }
    }
}
