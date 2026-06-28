package com.transcriptai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
    var title by remember { mutableStateOf("") }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Live timer + level meter while recording.
    LaunchedEffect(state) {
        while (state == RecState.RECORDING) {
            RecorderController.tick()
            elapsed = RecorderController.currentElapsedMs()
            amplitude = RecorderController.amplitude()
            delay(150)
        }
        if (state == RecState.PAUSED) elapsed = RecorderController.currentElapsedMs()
        if (state == RecState.IDLE) { elapsed = 0; amplitude = 0 }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("New recording") }, navigationIcon = {
            IconButton(onClick = { if (!RecorderController.isActive) nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
        }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(fmtMillis(elapsed), fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(
                when (state) { RecState.RECORDING -> "Recording…"; RecState.PAUSED -> "Paused"; else -> "Ready" },
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            // Simple level meter.
            val level = (amplitude.coerceIn(0, 20000)).toFloat() / 20000f
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth().height(8.dp))

            Spacer(Modifier.height(40.dp))

            if (!hasMic) {
                Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone permission") }
            } else {
                when (state) {
                    RecState.IDLE -> {
                        Button(
                            onClick = { RecorderController.start(context); RecorderService.start(context) },
                            modifier = Modifier.size(96.dp),
                            shape = MaterialTheme.shapes.large,
                        ) { Icon(Icons.Default.Mic, "Start", modifier = Modifier.size(40.dp)) }
                    }
                    RecState.RECORDING, RecState.PAUSED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state == RecState.RECORDING) {
                                FilledTonalButton(onClick = { RecorderController.pause() }, modifier = Modifier.size(72.dp)) {
                                    Icon(Icons.Default.Pause, "Pause", modifier = Modifier.size(30.dp))
                                }
                            } else {
                                FilledTonalButton(onClick = { RecorderController.resume() }, modifier = Modifier.size(72.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Resume", modifier = Modifier.size(30.dp))
                                }
                            }
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.size(72.dp),
                            ) { Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Recording continues in the background. Audio is uploaded and transcribed automatically; if you're offline it's saved and uploaded later.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
