package com.transcriptai.app.data

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Obtains a short-lived Google OAuth ACCESS token scoped for Calendar events + Tasks, which the
 * backend relays to the Google REST APIs. This is intentionally separate from sign-in: Credential
 * Manager gives an ID token for auth, whereas pushing action items to Calendar/Tasks needs an
 * *access* token with those scopes. If the user hasn't granted them yet, the first call surfaces a
 * one-tap consent dialog (the PendingIntent is launched through [launcher]).
 */
object GoogleWorkspaceAuth {
    val SCOPES = listOf(
        "https://www.googleapis.com/auth/calendar.events",
        "https://www.googleapis.com/auth/tasks",
    )

    /** Request authorization. On success calls [onToken] with the access token (or null), launching the
     *  consent UI through [launcher] first if the user must grant access. */
    fun authorize(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onToken: (String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES.map { Scope(it) })
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    runCatching {
                        launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    }.onFailure { onError(it.message ?: "Couldn't start Google authorization.") }
                } else {
                    onToken(result.accessToken)
                }
            }
            .addOnFailureListener { onError(it.message ?: "Google authorization failed.") }
    }

    /** Extract the access token from the consent-flow result returned to the launcher. */
    fun tokenFromResult(activity: Activity, data: Intent?): String? =
        runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()
}
