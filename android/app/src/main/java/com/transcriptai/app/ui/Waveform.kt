package com.transcriptai.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * WhatsApp-style live recording waveform. Each bar is one amplitude sample; new samples enter from
 * the right and scroll left, so the bar heights ride the voice's loudness/pitch in real time. Kept
 * deliberately lightweight (a single Canvas, no per-bar composables) so it stays smooth at ~8 fps.
 */
@Composable
fun Waveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    paused: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(72.dp)) {
        val maxBars = max(1, (size.width / 6.dp.toPx()).toInt())
        val barWidth = 3.dp.toPx()
        val gap = (size.width / maxBars) - barWidth
        val step = barWidth + gap
        val centerY = size.height / 2f
        // Right-align the most recent samples; pad on the left when we don't have enough yet.
        val recent = if (amplitudes.size > maxBars) amplitudes.takeLast(maxBars) else amplitudes
        val leftPad = maxBars - recent.size
        recent.forEachIndexed { i, raw ->
            val level = raw.coerceIn(0f, 1f)
            val barH = max(barWidth, level * (size.height - barWidth))
            val x = (leftPad + i) * step
            drawRoundRect(
                color = if (paused) barColor.copy(alpha = 0.4f) else barColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
