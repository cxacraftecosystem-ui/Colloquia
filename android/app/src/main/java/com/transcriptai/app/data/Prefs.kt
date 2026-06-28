package com.transcriptai.app.data

import android.content.Context

/** Local device preferences (recording capabilities + behaviour). These are device-specific, so they
 *  live in SharedPreferences rather than the per-user backend settings. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("colloquia_prefs", Context.MODE_PRIVATE)

    var noiseCancellation: Boolean
        get() = sp.getBoolean("noise_cancellation", true)
        set(v) = sp.edit().putBoolean("noise_cancellation", v).apply()

    var highQuality: Boolean
        get() = sp.getBoolean("high_quality", true)
        set(v) = sp.edit().putBoolean("high_quality", v).apply()

    var autoUpload: Boolean
        get() = sp.getBoolean("auto_upload", true)
        set(v) = sp.edit().putBoolean("auto_upload", v).apply()

    var notifications: Boolean
        get() = sp.getBoolean("notifications", true)
        set(v) = sp.edit().putBoolean("notifications", v).apply()

    var darkMode: Boolean?
        get() = if (!sp.contains("dark_mode")) null else sp.getBoolean("dark_mode", false)
        set(v) {
            if (v == null) sp.edit().remove("dark_mode").apply()
            else sp.edit().putBoolean("dark_mode", v).apply()
        }
}
