package top.wkbin.tianxuan.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「天玄 · TianXuan」Material 3 Expressive 设计系统色板
 * 融合 Google M3 Expressive 的生动高阶色调：
 * 曜石夜空（Obsidian Expressive Dark）与 温润素白（Alabaster Expressive Light）
 */

// M3 Expressive 主色系 (Indigo / Ultramarine)
val M3ExpLightPrimary = Color(0xFF4259C3)
val M3ExpLightOnPrimary = Color(0xFFFFFFFF)
val M3ExpLightPrimaryContainer = Color(0xFFDFE1FF)
val M3ExpLightOnPrimaryContainer = Color(0xFF001453)

val M3ExpDarkPrimary = Color(0xFFBAC3FF)
val M3ExpDarkOnPrimary = Color(0xFF0C2792)
val M3ExpDarkPrimaryContainer = Color(0xFF293FA9)
val M3ExpDarkOnPrimaryContainer = Color(0xFFDFE1FF)

// M3 Expressive 次色系 (Expressive Slate Iris)
val M3ExpLightSecondary = Color(0xFF5B5D72)
val M3ExpLightOnSecondary = Color(0xFFFFFFFF)
val M3ExpLightSecondaryContainer = Color(0xFFDFE1F9)
val M3ExpLightOnSecondaryContainer = Color(0xFF181A2C)

val M3ExpDarkSecondary = Color(0xFFC3C5DD)
val M3ExpDarkOnSecondary = Color(0xFF2C2F42)
val M3ExpDarkSecondaryContainer = Color(0xFF434559)
val M3ExpDarkOnSecondaryContainer = Color(0xFFDFE1F9)

// M3 Expressive 三级色 / 强调色 (Terracotta Coral / Rose)
val M3ExpLightTertiary = Color(0xFF944A32)
val M3ExpLightOnTertiary = Color(0xFFFFFFFF)
val M3ExpLightTertiaryContainer = Color(0xFFFFDBD1)
val M3ExpLightOnTertiaryContainer = Color(0xFF3B0900)

val M3ExpDarkTertiary = Color(0xFFFFB5A0)
val M3ExpDarkOnTertiary = Color(0xFF5A1C08)
val M3ExpDarkTertiaryContainer = Color(0xFF77321D)
val M3ExpDarkOnTertiaryContainer = Color(0xFFFFDBD1)

// M3 Expressive 状态色 (Semantic Feedback)
val M3ExpLightError = Color(0xFFBA1A1A)
val M3ExpLightErrorContainer = Color(0xFFFFDAD6)
val M3ExpDarkError = Color(0xFFFFB4AB)
val M3ExpDarkErrorContainer = Color(0xFF93000A)

// M3 Expressive 浅色表面层级 (Alabaster Warm Layers)
val M3ExpLightBackground = Color(0xFFFAF8FD)
val M3ExpLightOnBackground = Color(0xFF1A1B21)
val M3ExpLightSurface = Color(0xFFFAF8FD)
val M3ExpLightOnSurface = Color(0xFF1A1B21)
val M3ExpLightSurfaceVariant = Color(0xFFE2E2EC)
val M3ExpLightOnSurfaceVariant = Color(0xFF45464F)
val M3ExpLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val M3ExpLightSurfaceContainerLow = Color(0xFFF4F2F8)
val M3ExpLightSurfaceContainer = Color(0xFFEEEBF2)
val M3ExpLightSurfaceContainerHigh = Color(0xFFE8E5EC)
val M3ExpLightSurfaceContainerHighest = Color(0xFFE2DFE7)
val M3ExpLightOutline = Color(0xFF757680)
val M3ExpLightOutlineVariant = Color(0xFFC6C6D0)

// M3 Expressive 深色表面层级 (Obsidian Velvet Layers)
val M3ExpDarkBackground = Color(0xFF121318)
val M3ExpDarkOnBackground = Color(0xFFE3E2E9)
val M3ExpDarkSurface = Color(0xFF121318)
val M3ExpDarkOnSurface = Color(0xFFE3E2E9)
val M3ExpDarkSurfaceVariant = Color(0xFF45464F)
val M3ExpDarkOnSurfaceVariant = Color(0xFFC6C6D0)
val M3ExpDarkSurfaceContainerLowest = Color(0xFF0D0E13)
val M3ExpDarkSurfaceContainerLow = Color(0xFF1A1B21)
val M3ExpDarkSurfaceContainer = Color(0xFF1E1F25)
val M3ExpDarkSurfaceContainerHigh = Color(0xFF282A30)
val M3ExpDarkSurfaceContainerHighest = Color(0xFF33343B)
val M3ExpDarkOutline = Color(0xFF90909A)
val M3ExpDarkOutlineVariant = Color(0xFF45464F)

// ======================= 「澄明 · Chengming」液态玻璃主题色板 =======================
// 半透明冰霜质地，让根层流光（Aurora）透出并经由 backdrop 折射，呈现液态玻璃质感。
// 半透明度取折中：保留可读性，同时玻璃感足够明显。

// 澄明 · 浅色（对齐 AndroidLiquidGlass：背景全透显现壁纸折射，容器半透通澈）
val ChengmingLightPrimary = Color(0xFF0088FF)
val ChengmingLightOnPrimary = Color(0xFFFFFFFF)
val ChengmingLightPrimaryContainer = Color(0x330088FF)
val ChengmingLightOnPrimaryContainer = Color(0xFF0055B3)
val ChengmingLightSecondary = Color(0xFF0077D9)
val ChengmingLightOnSecondary = Color(0xFFFFFFFF)
val ChengmingLightSecondaryContainer = Color(0x2E0077D9)
val ChengmingLightOnSecondaryContainer = Color(0xFF004C8C)
val ChengmingLightTertiary = Color(0xFFFF8D28)
val ChengmingLightOnTertiary = Color(0xFFFFFFFF)
val ChengmingLightTertiaryContainer = Color(0x33FF8D28)
val ChengmingLightOnTertiaryContainer = Color(0xFF9E4B00)
val ChengmingLightError = Color(0xFFFF3B30)
val ChengmingLightOnError = Color(0xFFFFFFFF)
val ChengmingLightErrorContainer = Color(0x33FF3B30)
val ChengmingLightOnErrorContainer = Color(0xFF99140E)
// 背景全透，使整个视窗直接透出底层的几何壁纸作为真实折射源
val ChengmingLightBackground = Color.Transparent
val ChengmingLightOnBackground = Color(0xFF0F172A)
val ChengmingLightSurface = Color(0x52FFFFFF)
val ChengmingLightOnSurface = Color(0xFF0F172A)
val ChengmingLightSurfaceVariant = Color(0x3DF0F4F8)
val ChengmingLightOnSurfaceVariant = Color(0xFF334155)
val ChengmingLightSurfaceContainerLowest = Color(0x29FFFFFF)
val ChengmingLightSurfaceContainerLow = Color(0xB3FAFAFA)
val ChengmingLightSurfaceContainer = Color(0x6EFAFAFA)
val ChengmingLightSurfaceContainerHigh = Color(0x8AFAFAFA)
val ChengmingLightSurfaceContainerHighest = Color(0xA3FAFAFA)
val ChengmingLightOutline = Color(0x40FFFFFF)
val ChengmingLightOutlineVariant = Color(0x26FFFFFF)

// 澄明 · 深色（对齐 AndroidLiquidGlass：深邃透光暗色玻璃）
// 无自定义壁纸时的纯色底：与蒙版/表面色同基调，保证透明配色可读
val ChengmingNoWallpaperLightBase = Color(0xFFF0F4F8)
val ChengmingNoWallpaperDarkBase = Color(0xFF060B14)
val ChengmingDarkPrimary = Color(0xFF0091FF)
val ChengmingDarkOnPrimary = Color(0xFFFFFFFF)
val ChengmingDarkPrimaryContainer = Color(0x400091FF)
val ChengmingDarkOnPrimaryContainer = Color(0xFF80CAFF)
val ChengmingDarkSecondary = Color(0xFF2B9BF4)
val ChengmingDarkOnSecondary = Color(0xFFFFFFFF)
val ChengmingDarkSecondaryContainer = Color(0x382B9BF4)
val ChengmingDarkOnSecondaryContainer = Color(0xFF99D5FF)
val ChengmingDarkTertiary = Color(0xFFFF9F43)
val ChengmingDarkOnTertiary = Color(0xFF000000)
val ChengmingDarkTertiaryContainer = Color(0x40FF9F43)
val ChengmingDarkOnTertiaryContainer = Color(0xFFFFD1A4)
val ChengmingDarkError = Color(0xFFFF453A)
val ChengmingDarkOnError = Color(0xFFFFFFFF)
val ChengmingDarkErrorContainer = Color(0x40FF453A)
val ChengmingDarkOnErrorContainer = Color(0xFFFFB4AB)
// 深色背景同样全透，底图配合暗色层呈现通透夜色
val ChengmingDarkBackground = Color.Transparent
val ChengmingDarkOnBackground = Color(0xFFF8FAFC)
val ChengmingDarkSurface = Color(0x52121A28)
val ChengmingDarkOnSurface = Color(0xFFF8FAFC)
val ChengmingDarkSurfaceVariant = Color(0x38202E42)
val ChengmingDarkOnSurfaceVariant = Color(0xFFCBD5E1)
val ChengmingDarkSurfaceContainerLowest = Color(0x2E0B111D)
val ChengmingDarkSurfaceContainerLow = Color(0xB3121A26)
val ChengmingDarkSurfaceContainer = Color(0x6E17202F)
val ChengmingDarkSurfaceContainerHigh = Color(0x8A1D293B)
val ChengmingDarkSurfaceContainerHighest = Color(0xA3243247)
val ChengmingDarkOutline = Color(0x33FFFFFF)
val ChengmingDarkOutlineVariant = Color(0x1FFFFFFF)

