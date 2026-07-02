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
        // Every provider gets EXACTLY the fields its connector needs (Notion/Trello/Jira need more
        // than one — a single token box could never fully configure them).
        val fields = CONFIG_FIELDS[provider.key] ?: listOf("webhookUrl" to "Webhook URL")
        val values = remember(provider.key) { mutableStateMapOf<String, String>() }
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { connecting = null },
            icon = { BrandLogo(provider.icon, provider.name, 32.dp) },
            title = { Text("Connect ${provider.name}") },
            text = {
                Column {
                    Text(
                        CONNECT_HINTS[provider.key] ?: "Enter the ${provider.name} settings below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    fields.forEach { (key, label) ->
                        OutlinedTextField(
                            value = values[key] ?: "",
                            onValueChange = { values[key] = it },
                            singleLine = true,
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            trailingIcon = {
                                TextButton(onClick = { clipboard.getText()?.text?.let { values[key] = it.trim() } }) { Text("Paste") }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = fields.all { (k, _) -> !values[k].isNullOrBlank() },
                    onClick = {
                        vm.connectIntegration(provider.key, fields.associate { (k, _) -> k to (values[k] ?: "").trim() })
                        connecting = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { connecting = null }) { Text("Cancel") } },
        )
    }
}

// The exact config each outbound connector requires (mirrors backend services/integrations.py).
private val CONFIG_FIELDS: Map<String, List<Pair<String, String>>> = mapOf(
    "SLACK" to listOf("webhookUrl" to "Incoming webhook URL"),
    "TEAMS" to listOf("webhookUrl" to "Incoming webhook URL"),
    "WEBHOOK" to listOf("webhookUrl" to "Webhook URL"),
    "NOTION" to listOf("token" to "Integration token", "databaseId" to "Database ID"),
    "TRELLO" to listOf("key" to "API key", "token" to "API token", "idList" to "List ID"),
    "JIRA" to listOf(
        "baseUrl" to "Site URL (https://you.atlassian.net)",
        "email" to "Account email",
        "apiToken" to "API token",
        "projectKey" to "Project key",
    ),
)

private val CONNECT_HINTS: Map<String, String> = mapOf(
    "SLACK" to "Create an incoming webhook in Slack (App settings → Incoming Webhooks) and paste its URL.",
    "TEAMS" to "Add an Incoming Webhook connector to your Teams channel and paste its URL.",
    "WEBHOOK" to "Summaries and action items will be POSTed as JSON to this URL.",
    "NOTION" to "Create an internal integration at notion.so/my-integrations, share your database with it, and paste the token + database ID.",
    "TRELLO" to "Get your key and token from trello.com/power-ups/admin, and the list ID from the board (the list your cards should land in).",
    "JIRA" to "Use your Atlassian site URL, account email, an API token (id.atlassian.com), and the project key (e.g. PROJ).",
)

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
    // Always sit on white (like a real app tile) so brand marks — including dark ones like Notion —
    // stay legible in both light and dark themes.
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.size(size + 12.dp).semantics { contentDescription = "$name logo" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(size - 6.dp),
            )
        }
    }
}
