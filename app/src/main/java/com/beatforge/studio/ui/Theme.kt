package com.beatforge.studio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object BF {
    val Bg = Color(0xFF0A0A14)
    val Surface = Color(0xFF12122A)
    val Surface2 = Color(0xFF1A1A38)
    val Surface3 = Color(0xFF242452)
    val Outline = Color(0xFF2E2E5C)

    val Violet = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFFF2E93)
    val Lime = Color(0xFFA3E635)
    val Amber = Color(0xFFFBBF24)
    val Orange = Color(0xFFFB923C)
    val Blue = Color(0xFF60A5FA)
    val Red = Color(0xFFEF4444)
    val Teal = Color(0xFF2DD4BF)

    val Text = Color(0xFFF4F4FF)
    val TextDim = Color(0xFF9A9AC4)
    val TextFaint = Color(0xFF62628E)

    /** One accent colour per drum row. */
    val TrackColors = listOf(Pink, Cyan, Amber, Lime, Orange, Violet, Blue, Teal, Red)

    val Brand = Brush.linearGradient(listOf(Violet, Pink))
    val BrandHorizontal = Brush.horizontalGradient(listOf(Violet, Pink))
    val Cool = Brush.linearGradient(listOf(Cyan, Violet))
    val Screen = Brush.verticalGradient(listOf(Color(0xFF12122A), Bg))
}

private val Scheme = darkColorScheme(
    primary = BF.Violet,
    secondary = BF.Cyan,
    tertiary = BF.Pink,
    background = BF.Bg,
    surface = BF.Surface,
    surfaceVariant = BF.Surface2,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = BF.Text,
    onSurface = BF.Text,
    onSurfaceVariant = BF.TextDim,
    outline = BF.Outline,
    error = BF.Red
)

private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp)
)

@Composable
fun BeatForgeTheme(content: @Composable () -> Unit) {
    // The app is intentionally always dark: it's a studio, and the neon palette is tuned for it.
    @Suppress("UNUSED_VARIABLE") val ignored = isSystemInDarkTheme()
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
