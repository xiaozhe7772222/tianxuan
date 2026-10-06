package top.wkbin.tianxuan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppSans = FontFamily.SansSerif

val AppTypography = Typography(
    displaySmall = Typography().displaySmall.copy(
        fontFamily = AppSans,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = AppSans,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = AppSans,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = AppSans,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = AppSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = AppSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = AppSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = AppSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = AppSans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        // 字重减负：M3 规范 labelSmall 为 Medium，避免全 App 小标签默认加粗
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Apple Human Interface Guidelines (HIG) 标准排版比例与字距定义。
 * 遵循 iOS 原生 San Francisco (SF Pro) 的视觉字重阶梯与负向字距（Tight Tracking）：
 * - 大标题采用紧凑负字距，增强视觉凝聚力；
 * - 小标签与正文保持适度行距与阅读清晰度。
 */
object AppleTypography {
    val LargeTitle = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    )
    val Title1 = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )
    val Title2 = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    )
    val Title3 = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    )
    val Headline = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    )
    val Body = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp,
    )
    val Callout = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.3).sp,
    )
    val Subhead = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.2).sp,
    )
    val Footnote = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp,
    )
    val Caption1 = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val Caption2 = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val Button = androidx.compose.ui.text.TextStyle(
        fontFamily = AppSans,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
}

/**
 * 澄明（液态玻璃）主题专用的 Material 3 Typography 映射，采用 Apple HIG 比例。
 */
val LiquidGlassTypography = Typography(
    displaySmall = AppleTypography.LargeTitle,
    headlineLarge = AppleTypography.LargeTitle,
    headlineMedium = AppleTypography.Title1,
    headlineSmall = AppleTypography.Title2,
    titleLarge = AppleTypography.Title3,
    titleMedium = AppleTypography.Headline,
    titleSmall = AppleTypography.Subhead,
    bodyLarge = AppleTypography.Body,
    bodyMedium = AppleTypography.Callout,
    bodySmall = AppleTypography.Footnote,
    labelLarge = AppleTypography.Button,
    labelMedium = AppleTypography.Caption1,
    labelSmall = AppleTypography.Caption2,
)
