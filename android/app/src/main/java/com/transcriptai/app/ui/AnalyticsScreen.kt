package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.refreshAll() }
    val o = vm.overview
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            if (o == null) { CircularProgressIndicator(); return@Column }
            val mins = o.totalDurationSec / 60
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Recordings", o.totalRecordings.toString(), Modifier.weight(1f))
                StatCard("Minutes", mins.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Transcribed", o.transcribedRecordings.toString(), Modifier.weight(1f))
                StatCard("Favorites", o.favorites.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Action items", "${o.actionItemsCompleted}/${o.actionItemsTotal}", Modifier.weight(1f))
                StatCard("AI jobs", o.aiJobsCompleted.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("AI weekly digest")
            Row {
                Button(onClick = { vm.loadDigest("weekly") }, enabled = !vm.loading) { Text("Generate weekly") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.loadDigest("daily") }, enabled = !vm.loading) { Text("Daily") }
            }
            if (vm.loading) { Spacer(Modifier.height(12.dp)); CircularProgressIndicator() }
            vm.digestContent?.let {
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(it, Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
