package com.transcriptai.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.refreshAll() }
    var addOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders & tags") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { addOpen = true }) { Text("+") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item { SectionTitle("Folders") }
            item {
                ListItem(
                    headlineContent = { Text("All recordings") },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.clickable { vm.activeFolderId = null; vm.refreshRecordings(); nav.popBackStack() },
                )
            }
            items(vm.folders, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.name) },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    trailingContent = { IconButton(onClick = { vm.deleteFolder(f.id) }) { Icon(Icons.Default.Delete, "Delete") } },
                    modifier = Modifier.clickable { vm.activeFolderId = f.id; vm.refreshRecordings(); nav.popBackStack() },
                )
            }
            item { Spacer(Modifier.height(16.dp)); SectionTitle("Tags") }
            items(vm.tags, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text("#${t.name}") },
                    trailingContent = { IconButton(onClick = { vm.deleteTag(t.id) }) { Icon(Icons.Default.Delete, "Delete") } },
                )
            }
        }
    }

    if (addOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("New folder") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) vm.createFolder(name, null); addOpen = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } },
        )
    }
}
