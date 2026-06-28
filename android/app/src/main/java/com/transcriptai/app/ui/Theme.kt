package com.transcriptai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF5B5BD6)
private val IndigoDark = Color(0xFF4646B8)
private val Teal = Color(0xFF1FA8A0)

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Teal,
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF16161A),
    surface = Color.White,
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFECECF3),
    outline = Color(0xFFD9D9E3),
    error = Color(0xFFC64545),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9B9BF0),
    onPrimary = Color(0xFF101022),
    secondary = Teal,
    background = Color(0xFF121217),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF1B1B22),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF2A2A33),
    outline = Color(0xFF3A3A45),
    error = Color(0xFFE07A7A),
)

@Composable
fun TranscriptAITheme(darkMode: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkMode ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
