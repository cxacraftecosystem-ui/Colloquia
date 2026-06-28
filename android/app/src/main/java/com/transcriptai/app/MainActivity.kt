package com.transcriptai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.transcriptai.app.recording.Notifications
import com.transcriptai.app.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannel(this)
        setContent {
            ColloquiaTheme(darkMode = vm.darkMode) {
                val nav = rememberNavController()
                val start = if (vm.isLoggedIn) "library" else "login"
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
            }
        }
    }
}
