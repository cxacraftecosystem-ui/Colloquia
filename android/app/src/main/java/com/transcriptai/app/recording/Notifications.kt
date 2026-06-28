package com.transcriptai.app.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Local notifications for upload + processing status (no FCM in phase 1). */
object Notifications {
    const val STATUS_CHANNEL = "status_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(STATUS_CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(STATUS_CHANNEL, "Processing & uploads", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notif) }
    }
}
