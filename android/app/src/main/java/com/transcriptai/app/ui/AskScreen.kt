package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(vm: AppViewModel, nav: NavController) {
    var q by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask across recordings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                placeholder = { Text("Ask anything about your recordings…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(), maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (q.isNotBlank()) vm.ask(q.trim()) }, enabled = !vm.loading) { Text("Ask") }
            if (vm.loading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                vm.askAnswer?.let {
                    SectionTitle("Answer")
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(it, Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (vm.searchHits.isNotEmpty()) {
                    SectionTitle("Matching snippets")
                    vm.searchHits.forEach { hit ->
                        Text("• ${hit.text}", Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
