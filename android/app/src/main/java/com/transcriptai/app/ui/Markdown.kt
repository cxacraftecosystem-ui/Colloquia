package com.transcriptai.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A small, dependency-free Markdown renderer good enough for our AI output: headings, bold/italic,
 * inline code, bullet / numbered lists, and horizontal rules. The refined conversation is emitted as
 * `**Speaker N:**` lines separated by `---`, which this renders as a clean, readable dialogue.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { markdown.replace("\r\n", "\n").split("\n") }
    Column(modifier) {
        for (raw in blocks) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                trimmed == "---" || trimmed == "***" || trimmed == "___" ->
                    HorizontalDivider(
                        Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    )
                trimmed.startsWith("### ") -> Text(
                    inline(trimmed.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                trimmed.startsWith("## ") -> Text(
                    inline(trimmed.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                trimmed.startsWith("# ") -> Text(
                    inline(trimmed.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> BulletRow(
                    marker = "•", body = inline(trimmed.drop(2)),
                )
                Regex("^\\d+\\. .*").matches(trimmed) -> {
                    val dot = trimmed.indexOf('.')
                    BulletRow(marker = trimmed.substring(0, dot + 1), body = inline(trimmed.substring(dot + 2)))
                }
                else -> Text(
                    inline(line),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun BulletRow(marker: String, body: AnnotatedString) {
    Row(Modifier.padding(vertical = 1.dp, horizontal = 2.dp)) {
        Text("$marker ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Parse inline `**bold**`, `*italic*`/`_italic_`, and `` `code` `` into a styled AnnotatedString. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.length - i
        when {
            rest >= 4 && text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
