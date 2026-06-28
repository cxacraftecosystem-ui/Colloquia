package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Compose-friendly wrapper around ExoPlayer for in-app recording playback. Streams the presigned S3
 * URL (the bucket is private), supports play/pause/seek, and exposes position/duration as Compose
 * state so a transcript-timestamp tap can jump the audio to that moment.
 */
class RecordingPlayer(private val exo: ExoPlayer) {
    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableStateOf(0L); private set
    var durationMs by mutableStateOf(0L); private set
    var buffering by mutableStateOf(false); private set
    private var loadedUrl: String? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    durationMs = exo.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    /** Point the player at a URL exactly once (idempotent for the same URL). */
    fun ensureLoaded(url: String) {
        if (loadedUrl == url) return
        loadedUrl = url
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
    }

    fun togglePlay() { if (exo.isPlaying) exo.pause() else exo.play() }

    fun seekTo(ms: Long) {
        exo.seekTo(ms.coerceAtLeast(0L))
        exo.play()
    }

    fun nudge(deltaMs: Long) = seekTo((exo.currentPosition + deltaMs).coerceAtLeast(0L))

    fun syncPosition() {
        positionMs = exo.currentPosition.coerceAtLeast(0L)
        if (durationMs <= 0L) durationMs = exo.duration.coerceAtLeast(0L)
    }

    fun release() = exo.release()
}

@Composable
fun rememberRecordingPlayer(): RecordingPlayer {
    val context = LocalContext.current
    val exo = remember { ExoPlayer.Builder(context).build() }
    val player = remember { RecordingPlayer(exo) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(player) {
        while (true) { player.syncPosition(); delay(400) }
    }
    return player
}

/** Compact transport bar shown above the tabs once a recording's audio URL is available. */
@Composable
fun PlaybackBar(player: RecordingPlayer, audioUrl: String, modifier: Modifier = Modifier) {
    val total = player.durationMs.coerceAtLeast(0L)
    val pos = player.positionMs.coerceIn(0L, if (total > 0) total else Long.MAX_VALUE)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.ensureLoaded(audioUrl); player.nudge(-10_000) }) {
                    Icon(Icons.Default.Replay10, "Back 10s")
                }
                FilledIconButton(
                    onClick = { player.ensureLoaded(audioUrl); player.togglePlay() },
                    modifier = Modifier.size(48.dp),
                ) {
                    if (player.buffering) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(
                            if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (player.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                IconButton(onClick = { player.ensureLoaded(audioUrl); player.nudge(10_000) }) {
                    Icon(Icons.Default.Forward10, "Forward 10s")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${fmtMs(pos.toInt())} / ${if (total > 0) fmtMs(total.toInt()) else "--:--"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = if (total > 0) pos.toFloat() / total else 0f,
                onValueChange = { frac -> if (total > 0) { player.ensureLoaded(audioUrl); player.seekTo((frac * total).toLong()) } },
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
        }
    }
}
