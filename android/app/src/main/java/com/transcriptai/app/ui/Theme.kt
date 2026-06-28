package com.transcriptai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ElevenLabs-inspired palette (see DESIGN-elevenlabs.md): off-white editorial canvas, warm near-black
// ink, no saturated CTA colour. The ink IS the primary. Pastel gradient tokens are decoration only.
private val Ink = Color(0xFF0C0A09)
private val InkPrimary = Color(0xFF292524)
private val Body = Color(0xFF4E4E4E)
private val Muted = Color(0xFF777169)
private val MutedSoft = Color(0xFFA8A29E)
private val Hairline = Color(0xFFE7E5E4)
private val Canvas = Color(0xFFF5F5F5)
private val SurfaceCard = Color(0xFFFFFFFF)
private val SurfaceStrong = Color(0xFFF0EFED)
private val CanvasDeep = Color(0xFF0C0A09)
private val SurfaceDarkElevated = Color(0xFF1C1917)

// Atmospheric gradient stops (decoration only).
val GradientMint = Color(0xFFA7E5D3)
val GradientPeach = Color(0xFFF4C5A8)
val GradientLavender = Color(0xFFC8B8E0)
val GradientSky = Color(0xFFA8C8E8)
val GradientRose = Color(0xFFE8B8C4)

private val SemanticError = Color(0xFFDC2626)

private val Light = lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    secondary = InkPrimary,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = SurfaceStrong,
    onSurfaceVariant = Body,
    outline = Hairline,
    outlineVariant = Color(0xFFD6D3D1),
    error = SemanticError,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = CanvasDeep,
    secondary = Color(0xFFE7E5E4),
    onSecondary = CanvasDeep,
    background = CanvasDeep,
    onBackground = Color(0xFFF5F5F5),
    surface = SurfaceDarkElevated,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2622),
    onSurfaceVariant = MutedSoft,
    outline = Color(0xFF3A3530),
    error = Color(0xFFE07A7A),
)

// Pill CTAs + soft 16px cards, per the design's rounded scale.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Display = serif (Waldenburg substitute) at light weight; body/labels = sans (Inter substitute) with
// the design's subtle +0.15px tracking. No font files shipped, so we approximate with system families.
private val Display = FontFamily.Serif
private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 36.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 0.18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.96.sp),
)

@Composable
fun ColloquiaTheme(darkMode: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkMode ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
