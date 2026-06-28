package com.transcriptai.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.transcriptai.app.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.transcriptai.app.AppViewModel
import com.transcriptai.app.data.GoogleAuthClient
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel, activity: ComponentActivity, nav: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val google = remember { GoogleAuthClient(activity) }

    fun goLibrary() {
        nav.navigate("library") { popUpTo("login") { inclusive = true } }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Colloquia logo",
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Colloquia", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Record. Transcribe. Understand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.login(email, password) { ok -> if (ok) goLibrary() } },
            enabled = !vm.loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign in") }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val token = google.getIdToken()
                        vm.loginGoogle(token) { ok -> if (ok) goLibrary() }
                    } catch (e: Exception) {
                        vm.error = e.message ?: "Google sign-in failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_g),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("Continue with Google")
        }

        if (vm.loading) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator() }
        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
