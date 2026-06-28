package com.transcriptai.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.R

private data class Provider(
    val key: String,
    val name: String,
    val desc: String,
    @DrawableRes val icon: Int,
)

// Google ecosystem first — these light up with the Google account you sign in with.
private val GOOGLE_WORKSPACE = listOf(
    Provider("GMAIL", "Gmail", "Email a summary to yourself or your team", R.drawable.ic_gmail),
    Provider("GOOGLE_CALENDAR", "Google Calendar", "Turn deadlines into calendar events", R.drawable.ic_google_calendar),
    Provider("GOOGLE_TASKS", "Google Tasks", "Send action items to your task list", R.drawable.ic_google_tasks),
    Provider("GOOGLE_MEET", "Google Meet", "Capture and transcribe your meetings", R.drawable.ic_google_meet),
)

// Other destinations you can connect with a webhook URL or API token.
private val DESTINATIONS = listOf(
    Provider("SLACK", "Slack", "Post summaries to a channel", R.drawable.ic_slack),
    Provider("TEAMS", "Microsoft Teams", "Post to a Teams channel", R.drawable.ic_teams),
    Provider("NOTION", "Notion", "Save notes to a Notion page", R.drawable.ic_notion),
    Provider("TRELLO", "Trello", "Create cards from action items", R.drawable.ic_trello),
    Provider("JIRA", "Jira", "Create issues from action items", R.drawable.ic_jira),
    Provider("WEBHOOK", "Webhook", "Send to any custom URL", R.drawable.ic_webhook),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(vm: AppViewModel, nav: NavController) {
    LaunchedEffect(Unit) { vm.loadIntegrations() }
    var connecting by remember { mutableStateOf<Provider?>(null) }
    val connected = vm.integrations.associateBy { it.provider }
    val googleLinked = vm.user?.authProvider == "GOOGLE"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Send your summaries and action items where you already work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            item { SectionHeader("Google Workspace") }
            items(GOOGLE_WORKSPACE) { p ->
                ProviderRow(
                    provider = p,
                    statusText = if (googleLinked) "Linked to your Google account" else "Sign in with Google to link",
                    trailing = {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (googleLinked) "Linked" else "Google") },
                        )
                    },
                )
            }

            item { Spacer(Modifier.height(12.dp)); SectionHeader("Other destinations") }
            items(DESTINATIONS) { p ->
                val isOn = connected.containsKey(p.key)
                ProviderRow(
                    provider = p,
                    statusText = if (isOn) "Connected" else p.desc,
                    trailing = {
                        if (isOn) {
                            TextButton(onClick = { vm.disconnectIntegration(p.key) }) { Text("Disconnect") }
                        } else {
                            FilledTonalButton(onClick = { connecting = p }) { Text("Connect") }
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    connecting?.let { provider ->
        var value by remember { mutableStateOf("") }
        val needsWebhook = provider.key in listOf("SLACK", "TEAMS", "WEBHOOK")
        AlertDialog(
            onDismissRequest = { connecting = null },
            icon = { BrandLogo(provider.icon, provider.name, 32.dp) },
            title = { Text("Connect ${provider.name}") },
            text = {
                Column {
                    Text(
                        if (needsWebhook) "Paste the incoming webhook URL from ${provider.name}."
                        else "Paste your ${provider.name} API token.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text(if (needsWebhook) "Webhook URL" else "API token") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = value.isNotBlank(),
                    onClick = {
                        val cfg = if (needsWebhook) mapOf("webhookUrl" to value) else mapOf("token" to value)
                        vm.connectIntegration(provider.key, cfg); connecting = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { connecting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProviderRow(provider: Provider, statusText: String, trailing: @Composable () -> Unit) {
    ListItem(
        leadingContent = { BrandLogo(provider.icon, provider.name, 36.dp) },
        headlineContent = { Text(provider.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(statusText, style = MaterialTheme.typography.bodySmall) },
        trailingContent = trailing,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun BrandLogo(@DrawableRes icon: Int, name: String, size: androidx.compose.ui.unit.Dp) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(size + 12.dp).semantics { contentDescription = "$name logo" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(size - 4.dp),
            )
        }
    }
}
