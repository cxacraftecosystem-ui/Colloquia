package com.transcriptai.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.recording.RecState
import com.transcriptai.app.recording.RecorderController
import com.transcriptai.app.recording.RecorderService
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val state by RecorderController.state.collectAsState()
    var elapsed by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }
    val waveform = remember { mutableStateListOf<Float>() }
    var title by remember { mutableStateOf("") }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Pick an audio file -> upload as-is.
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = queryName(context, uri)
            vm.importMedia(uri, name, isVideo = false, title = title.ifBlank { name.substringBeforeLast('.') })
            nav.popBackStack()
        }
    }
    // Pick a video file -> audio is stripped on-device, then uploaded.
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = queryName(context, uri)
            vm.importMedia(uri, name, isVideo = true, title = title.ifBlank { name.substringBeforeLast('.') })
            nav.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state) {
        while (state == RecState.RECORDING) {
            RecorderController.tick()
            elapsed = RecorderController.currentElapsedMs()
            amplitude = RecorderController.amplitude()
            // Perceptual scaling (sqrt) so quiet speech still moves the bars; floor keeps a visible tick.
            val level = kotlin.math.sqrt((amplitude.coerceIn(0, 32767)).toFloat() / 32767f).coerceIn(0.04f, 1f)
            waveform.add(level)
            if (waveform.size > 240) waveform.removeAt(0)
            delay(90)
        }
        if (state == RecState.PAUSED) elapsed = RecorderController.currentElapsedMs()
        if (state == RecState.IDLE) { elapsed = 0; amplitude = 0; waveform.clear() }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("New recording") }, navigationIcon = {
                IconButton(onClick = { if (!RecorderController.isActive) nav.popBackStack() else confirmDiscard = true }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            })
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(fmtMillis(elapsed), fontSize = 60.sp, fontWeight = FontWeight.Light)
            Text(
                when (state) { RecState.RECORDING -> "Recording…"; RecState.PAUSED -> "Paused"; else -> "Ready" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.noiseCancellation && state != RecState.IDLE) {
                Text("Noise cancellation on", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            // Live WhatsApp-style waveform that rides the voice's loudness while recording.
            Waveform(amplitudes = waveform, paused = state == RecState.PAUSED)

            Spacer(Modifier.height(36.dp))

            if (!hasMic) {
                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone permission") }
            } else {
                when (state) {
                    RecState.IDLE -> {
                        Button(
                            onClick = { RecorderController.start(context, vm.noiseCancellation, vm.highQuality); RecorderService.start(context) },
                            modifier = Modifier.size(96.dp),
                            shape = MaterialTheme.shapes.large,
                        ) { Icon(Icons.Default.Mic, "Start", modifier = Modifier.size(52.dp)) }
                    }
                    RecState.RECORDING, RecState.PAUSED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Discard
                            FilledTonalButton(
                                onClick = { confirmDiscard = true },
                                modifier = Modifier.size(64.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) { Icon(Icons.Default.Delete, "Discard", modifier = Modifier.size(32.dp)) }
                            // Pause / resume
                            if (state == RecState.RECORDING) {
                                FilledTonalButton(onClick = { RecorderController.pause() }, modifier = Modifier.size(72.dp), contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.Pause, "Pause", modifier = Modifier.size(40.dp))
                                }
                            } else {
                                FilledTonalButton(onClick = { RecorderController.resume() }, modifier = Modifier.size(72.dp), contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Resume", modifier = Modifier.size(40.dp))
                                }
                            }
                            // Stop & save
                            Button(
                                onClick = {
                                    val result = RecorderController.stop()
                                    RecorderService.stop(context)
                                    if (result != null) {
                                        val (file: File, dur: Int) = result
                                        val finalTitle = title.ifBlank { "Recording ${System.currentTimeMillis() / 1000}" }
                                        vm.finishRecordingAndUpload(file, dur, finalTitle)
                                    }
                                    nav.popBackStack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(72.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Icon(Icons.Default.Check, "Stop & save", modifier = Modifier.size(40.dp)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state == RecState.IDLE) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))
                Text("Or import a file", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.AudioFile, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Audio")
                    }
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Video")
                    }
                }
                Text(
                    "Video is processed on your device — only the audio is uploaded and transcribed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Recording continues in the background. Audio is uploaded and transcribed automatically; if you're offline it's saved and uploaded later.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard recording?") },
            text = { Text("This recording will be deleted and not saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    RecorderController.discard()
                    RecorderService.stop(context)
                    nav.popBackStack()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep recording") } },
        )
    }
}

private fun queryName(context: Context, uri: Uri): String {
    var name = "import"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { name = it }
        }
    }
    return name
}
