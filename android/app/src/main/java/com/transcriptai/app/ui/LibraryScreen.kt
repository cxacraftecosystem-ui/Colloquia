package com.transcriptai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.data.RecordingDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) {
        vm.refreshAll()
        vm.flushOutbox()
        vm.registerPushToken()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { nav.navigate("ask") }) { Icon(Icons.Default.AutoAwesome, "Ask AI") }
                    IconButton(onClick = { nav.navigate("integrations") }) { Icon(Icons.Default.Hub, "Integrations") }
                    IconButton(onClick = { nav.navigate("folders") }) { Icon(Icons.Default.Folder, "Folders") }
                    IconButton(onClick = { nav.navigate("analytics") }) { Icon(Icons.Default.BarChart, "Analytics") }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("record") },
                icon = { Icon(Icons.Default.Mic, null) },
                text = { Text("Record") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                placeholder = { Text("Search recordings & transcripts") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { vm.refreshRecordings() }) { Icon(Icons.Default.ArrowForward, "Search") }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = vm.favoritesOnly, onClick = { vm.favoritesOnly = !vm.favoritesOnly; vm.refreshRecordings() }, label = { Text("Favorites") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = vm.showArchived, onClick = { vm.showArchived = !vm.showArchived; vm.refreshRecordings() }, label = { Text("Archived") })
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {
                    vm.sort = when (vm.sort) { "recent" -> "title"; "title" -> "duration"; "duration" -> "oldest"; else -> "recent" }
                    vm.refreshRecordings()
                }, label = { Text("Sort: ${vm.sort}") })
            }

            if (vm.pendingUploads > 0) {
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${vm.pendingUploads} recording(s) waiting to upload", Modifier.weight(1f))
                        TextButton(onClick = { vm.flushOutbox() }) { Text("Upload now") }
                    }
                }
            }

            vm.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            if (vm.loading && vm.recordings.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (vm.recordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recordings yet. Tap Record to start.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.recordings, key = { it.id }) { rec ->
                        RecordingRow(rec, onClick = { nav.navigate("detail/${rec.id}") },
                            onFavorite = { vm.toggleFavorite(rec.id) }, onPin = { vm.togglePin(rec.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: RecordingDto, onClick: () -> Unit, onFavorite: () -> Unit, onPin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rec.isPinned) { Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                Text(rec.aiTitle ?: rec.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(rec.transcriptStatus)
                Spacer(Modifier.width(8.dp))
                Text(fmtDuration(rec.durationSec), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                rec.folder?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("• ${it.name}", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        IconButton(onClick = onPin) {
            Icon(Icons.Default.PushPin, "Pin", tint = if (rec.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (rec.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite",
                tint = if (rec.isFavorite) Color(0xFFE0556B) else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
