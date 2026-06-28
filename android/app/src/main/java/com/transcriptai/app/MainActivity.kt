package com.transcriptai.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.transcriptai.app.data.OAuthClient
import com.transcriptai.app.recording.Notifications
import com.transcriptai.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val deepLink = mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.data
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        deepLink.value = intent?.data
        setContent {
            ColloquiaTheme(darkMode = vm.darkMode) {
                val nav = rememberNavController()
                val start = if (vm.isLoggedIn) "library" else "login"

                // Check for an OTA update once we're signed in.
                LaunchedEffect(vm.isLoggedIn) { if (vm.isLoggedIn) vm.checkForUpdate(silent = true) }

                // Handle the OAuth redirect (Microsoft / Yahoo) -> exchange the code, then go home.
                val link = deepLink.value
                LaunchedEffect(link) {
                    if (link != null && link.scheme == "com.transcriptai.app") {
                        val provider = OAuthClient.providerForState(link.getQueryParameter("state"))
                        val code = link.getQueryParameter("code")
                        if (provider != null && code != null) {
                            vm.loginOAuth(provider, code) { ok ->
                                if (ok) nav.navigate("library") { popUpTo("login") { inclusive = true } }
                            }
                        } else {
                            vm.error = link.getQueryParameter("error_description")
                                ?: link.getQueryParameter("error")?.let { "Sign-in was cancelled." }
                        }
                        deepLink.value = null
                    }
                }

                NavHost(navController = nav, startDestination = start) {
                    composable("login") { LoginScreen(vm, this@MainActivity, nav) }
                    composable("library") { LibraryScreen(vm, nav) }
                    composable("record") { RecordScreen(vm, nav) }
                    composable("detail/{id}") { back ->
                        DetailScreen(vm, nav, back.arguments?.getString("id") ?: "")
                    }
                    composable("settings") { SettingsScreen(vm, nav) }
                    composable("analytics") { AnalyticsScreen(vm, nav) }
                    composable("folders") { FoldersScreen(vm, nav) }
                    composable("ask") { AskScreen(vm, nav) }
                    composable("integrations") { IntegrationsScreen(vm, nav) }
                }

                // App-wide update prompt (soft: dismissible). Re-shows next launch until updated.
                var dismissed by remember { mutableStateOf(false) }
                val rel = vm.updateRelease
                if (rel != null && !dismissed) {
                    AlertDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text("Update available") },
                        text = {
                            Column {
                                Text("Colloquia ${rel.versionName} is available. You have ${BuildConfig.VERSION_NAME}.")
                                rel.notes?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { vm.downloadAndInstallUpdate() }, enabled = !vm.updateBusy) {
                                Text(if (vm.updateBusy) "Downloading…" else "Update now")
                            }
                        },
                        dismissButton = { TextButton(onClick = { dismissed = true }) { Text("Later") } },
                    )
                }
            }
        }
    }
}
