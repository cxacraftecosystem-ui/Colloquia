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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.transcriptai.app.R

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

// Design-adherent type (DESIGN-elevenlabs.md): EB Garamond (the documented Waldenburg-Light substitute)
// for display serif, Inter for body/labels. Both are bundled as variable fonts; we pin the weight axis
// per style. EB Garamond's wght axis starts at 400, so display sits at 400 (its lightest) — the
// editorial signature. Body Inter carries the design's subtle +0.15px tracking.
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun garamond(weight: Int) = Font(
    R.font.eb_garamond_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
private val Display = FontFamily(garamond(400), garamond(500), garamond(600))
private val Sans = Inter

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(400), fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(400), fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(400), fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight(400), fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(400), fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight(500), fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(500), fontSize = 13.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 12.sp, letterSpacing = 0.8.sp),
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
