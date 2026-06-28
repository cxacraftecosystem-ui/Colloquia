package com.transcriptai.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun fmtDuration(seconds: Int?): String {
    val s = seconds ?: 0
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtMillis(ms: Long): String {
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

fun fmtMs(ms: Int): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status.uppercase()) {
        "COMPLETED" -> "Transcribed" to Color(0xFF1FA8A0)
        "PROCESSING" -> "Processing" to Color(0xFFE08A1F)
        "QUEUED", "PENDING" -> "Queued" to Color(0xFF8A8AA0)
        "RATE_LIMITED" -> "Rate-limited" to Color(0xFFE08A1F)
        "FAILED" -> "Failed" to Color(0xFFC64545)
        "UNAVAILABLE" -> "AI off" to Color(0xFF8A8AA0)
        "EMPTY" -> "No speech" to Color(0xFF8A8AA0)
        else -> status to Color(0xFF8A8AA0)
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
