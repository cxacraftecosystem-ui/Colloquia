package com.transcriptai.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel

private val PROVIDERS = listOf("SLACK", "TEAMS", "NOTION", "TRELLO", "JIRA", "WEBHOOK")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.loadIntegrations() }
    var connecting by remember { mutableStateOf<String?>(null) }
    val connected = vm.integrations.associateBy { it.provider }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Integrations") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item { Text("Connect a destination to push summaries & action items.", color = MaterialTheme.colorScheme.outline); Spacer(Modifier.height(8.dp)) }
            items(PROVIDERS) { p ->
                val isOn = connected.containsKey(p)
                ListItem(
                    headlineContent = { Text(p) },
                    supportingContent = { Text(if (isOn) "Connected" else "Not connected") },
                    trailingContent = {
                        if (isOn) TextButton(onClick = { vm.disconnectIntegration(p) }) { Text("Disconnect") }
                        else TextButton(onClick = { connecting = p }) { Text("Connect") }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    connecting?.let { provider ->
        var value by remember { mutableStateOf("") }
        val needsWebhook = provider in listOf("SLACK", "TEAMS", "WEBHOOK")
        AlertDialog(
            onDismissRequest = { connecting = null },
            title = { Text("Connect $provider") },
            text = {
                Column {
                    Text(if (needsWebhook) "Paste the incoming webhook URL." else "Paste the API token.")
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cfg = if (needsWebhook) mapOf("webhookUrl" to value) else mapOf("token" to value)
                    vm.connectIntegration(provider, cfg); connecting = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { connecting = null }) { Text("Cancel") } },
        )
    }
}
