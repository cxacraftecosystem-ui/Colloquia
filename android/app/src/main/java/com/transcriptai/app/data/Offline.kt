package com.transcriptai.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PendingUpload(
    val localId: String,
    val filePath: String,
    val title: String,
    val durationSec: Int,
    val mimeType: String = "audio/m4a",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Persistent upload outbox. Recordings made offline (or whose upload fails) are queued here as JSON
 * pointing at the already-saved audio file, and flushed on reconnect. Mirrors the field-repo's
 * offline approach: the audio bytes live on disk, only lightweight metadata is serialized.
 */
class Outbox(context: Context) {
    private val file = File(context.filesDir, "upload_outbox.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; prettyPrint = true }

    @Synchronized
    fun all(): List<PendingUpload> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(PendingUpload.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(item: PendingUpload) {
        write(all() + item)
    }

    @Synchronized
    fun remove(localId: String) {
        write(all().filterNot { it.localId == localId })
    }

    private fun write(items: List<PendingUpload>) {
        file.writeText(
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(PendingUpload.serializer()), items)
        )
    }
}
