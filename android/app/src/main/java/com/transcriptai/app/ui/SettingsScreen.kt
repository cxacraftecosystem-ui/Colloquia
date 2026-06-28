package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.BuildConfig

private val TRANSCRIPTION_MODES = listOf(
    "RAW" to "Raw — verbatim, fastest",
    "REFINED" to "Refined — cleaned up & punctuated",
    "REFINED_TRANSLATED" to "Refined + translated to English",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.loadAppSettings() }
    var pushNotes by remember { mutableStateOf("") }
    var showPush by remember { mutableStateOf(false) }
    var pushResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            vm.user?.let {
                Text(it.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(it.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.isMasterAdmin) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Master admin") }, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Appearance")
            SettingRow("Dark mode", "Off uses your system theme") {
                Switch(checked = vm.darkMode == true, onCheckedChange = { vm.saveDarkMode(if (it) true else null) })
            }

            SectionTitle("Recording")
            SettingRow("Noise cancellation", "Cleaner audio via voice pre-processing") {
                Switch(checked = vm.noiseCancellation, onCheckedChange = { vm.saveNoiseCancellation(it) })
            }
            SettingRow("High quality", if (vm.highQuality) "128 kbps · 44.1 kHz" else "64 kbps · 22 kHz") {
                Switch(checked = vm.highQuality, onCheckedChange = { vm.saveHighQuality(it) })
            }
            SettingRow("Auto-upload after recording", "Upload as soon as you stop") {
                Switch(checked = vm.autoUpload, onCheckedChange = { vm.saveAutoUpload(it) })
            }

            SectionTitle("Transcription")
            Text(
                if (vm.isMasterAdmin) "How transcripts are produced (applies to everyone)."
                else "How transcripts are produced. Only a master admin can change this.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TRANSCRIPTION_MODES.forEach { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = vm.transcriptionMode == mode, enabled = vm.isMasterAdmin) { vm.chooseTranscriptionMode(mode) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = vm.transcriptionMode == mode, enabled = vm.isMasterAdmin, onClick = { vm.chooseTranscriptionMode(mode) })
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = if (vm.isMasterAdmin) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionTitle("Notifications")
            SettingRow("Processing & upload notifications", null) {
                Switch(checked = vm.notifications, onCheckedChange = { vm.saveNotifications(it) })
            }

            SectionTitle("Storage")
            Text("${vm.pendingUploads} recording(s) pending upload", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.flushOutbox() }) { Text("Upload pending now") }

            SectionTitle("About")
            Text("Colloquia ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.checkForUpdate(silent = false) }) { Text("Check for updates") }
            vm.updateRelease?.let { rel ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Update available: ${rel.versionName}", fontWeight = FontWeight.SemiBold)
                        rel.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Button(onClick = { vm.downloadAndInstallUpdate() }, enabled = !vm.updateBusy, modifier = Modifier.padding(top = 8.dp)) {
                            if (vm.updateBusy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text("Download & install")
                        }
                    }
                }
            }

            if (vm.isMasterAdmin) {
                SectionTitle("Master admin")
                Text("Publish this installed build to every device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { showPush = true },
                    enabled = !vm.publishingUpdate,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (vm.publishingUpdate) "Publishing…" else "Push update to all")
                }
                pushResult?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { vm.logout(); nav.navigate("login") { popUpTo(0) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showPush) {
        AlertDialog(
            onDismissRequest = { showPush = false },
            title = { Text("Push update to all") },
            text = {
                Column {
                    Text("This uploads version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) and prompts every device to update.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = pushNotes, onValueChange = { pushNotes = it }, label = { Text("Release notes (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPush = false
                    vm.pushUpdateToAll(pushNotes.ifBlank { null }) { ok -> pushResult = if (ok) "Update published to all devices." else null }
                }) { Text("Publish") }
            },
            dismissButton = { TextButton(onClick = { showPush = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(label: String, subtitle: String?, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}
