package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    var autoUpload by remember { mutableStateOf(true) }
    var autoTranscribe by remember { mutableStateOf(true) }
    var autoSummary by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    var highQuality by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            vm.user?.let { Text(it.name, fontWeight = FontWeight.SemiBold); Text(it.email, color = MaterialTheme.colorScheme.outline) }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Appearance")
            SettingRow("Dark mode") {
                // Tri-state: System default unless explicitly toggled.
                Switch(checked = vm.darkMode == true, onCheckedChange = { vm.saveDarkMode(if (it) true else false) })
            }
            TextButton(onClick = { vm.saveDarkMode(null) }) { Text("Use system theme") }

            SectionTitle("Recording")
            SettingRow("High quality (128 kbps)") { Switch(checked = highQuality, onCheckedChange = { highQuality = it }) }
            SettingRow("Auto-upload after recording") { Switch(checked = autoUpload, onCheckedChange = { autoUpload = it }) }

            SectionTitle("Transcription & AI")
            SettingRow("Auto-transcribe") { Switch(checked = autoTranscribe, onCheckedChange = { autoTranscribe = it }) }
            SettingRow("Auto-generate summaries") { Switch(checked = autoSummary, onCheckedChange = { autoSummary = it }) }

            SectionTitle("Notifications")
            SettingRow("Processing & upload notifications") { Switch(checked = notifications, onCheckedChange = { notifications = it }) }

            SectionTitle("Storage")
            Text("${vm.pendingUploads} recording(s) pending upload", color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = { vm.flushOutbox() }) { Text("Upload pending now") }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.logout(); nav.navigate("login") { popUpTo(0) } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Sign out") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        control()
    }
}
