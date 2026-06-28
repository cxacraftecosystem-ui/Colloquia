package com.transcriptai.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.transcriptai.app.recording.Notifications

/**
 * Receives FCM pushes (processing-complete, reminders) and surfaces them as local notifications.
 * Requires google-services.json + the google-services Gradle plugin to actually initialize FCM;
 * without it the app still builds and runs (this service simply never fires).
 */
class ColloquiaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // The app re-registers its token on next login/launch via AppViewModel.registerPushToken().
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "Colloquia"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        Notifications.notify(applicationContext, System.currentTimeMillis().toInt(), title, body)
    }
}
