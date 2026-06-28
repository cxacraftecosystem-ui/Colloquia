package com.transcriptai.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.util.UUID

/**
 * Single point of access to the backend + local stores. Owns the upload pipeline
 * (presign -> PUT to S3 -> /complete) with offline fallback to the [Outbox].
 */
class Repository(context: Context) {
    private val appContext = context.applicationContext
    val tokenStore = TokenStore(appContext)
    val api: TranscriptApi = ApiClient.create(tokenStore)
    private val http: OkHttpClient = ApiClient.okHttp(tokenStore)
    private val outbox = Outbox(appContext)

    val isLoggedIn: Boolean get() = !tokenStore.getToken().isNullOrBlank()
    fun cachedUser(): UserDto? = tokenStore.getUser()

    suspend fun login(email: String, password: String): UserDto {
        val res = api.login(LoginRequest(email = email, password = password))
        tokenStore.setToken(res.accessToken)
        tokenStore.setUser(res.user)
        return res.user
    }

    suspend fun loginGoogle(idToken: String): UserDto {
        val res = api.login(LoginRequest(googleIdToken = idToken))
        tokenStore.setToken(res.accessToken)
        tokenStore.setUser(res.user)
        return res.user
    }

    fun logout() = tokenStore.clear()

    // ---------------------------------------------------------------- upload

    /** Upload a recorded file. On any network error the recording is queued to the outbox and the
     *  caller is told it's pending (so nothing is lost offline). Returns the created recording or null
     *  if queued. */
    suspend fun uploadRecording(file: File, durationSec: Int, title: String, transcribe: Boolean = true): RecordingDto? {
        return try {
            doUpload(file, durationSec, title, transcribe)
        } catch (e: Exception) {
            outbox.add(
                PendingUpload(
                    localId = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    title = title,
                    durationSec = durationSec,
                )
            )
            null
        }
    }

    private suspend fun doUpload(file: File, durationSec: Int, title: String, transcribe: Boolean): RecordingDto {
        val mime = "audio/m4a"
        val presign = api.presign(PresignRequest(filename = file.name, mimeType = mime, sizeBytes = file.length()))
        // Stream the bytes straight to S3 with the presigned PUT (never through the backend).
        val body = file.asRequestBody(mime.toMediaTypeOrNull())
        val putReq = Request.Builder()
            .url(presign.uploadUrl)
            .put(body)
            .header("Content-Type", mime)
            .build()
        http.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("S3 upload failed: HTTP ${resp.code}")
        }
        return api.completeUpload(
            CompleteUploadRequest(
                objectKey = presign.objectKey,
                bucket = presign.bucket,
                url = presign.publicUrl,
                mimeType = mime,
                originalFilename = file.name,
                sizeBytes = file.length(),
                durationSec = durationSec,
                title = title,
                transcribe = transcribe,
            )
        )
    }

    /** Try to upload everything queued offline. Returns how many succeeded. */
    suspend fun flushOutbox(): Int {
        var done = 0
        for (item in outbox.all()) {
            val file = File(item.filePath)
            if (!file.exists()) {
                outbox.remove(item.localId)
                continue
            }
            try {
                doUpload(file, item.durationSec, item.title, transcribe = true)
                outbox.remove(item.localId)
                done++
            } catch (_: Exception) {
                // Leave it queued; try again next reconnect.
            }
        }
        return done
    }

    fun pendingCount(): Int = outbox.all().size

    /** Download an export (txt/md/pdf/docx) through the authenticated API and return the bytes. */
    suspend fun exportBytes(recordingId: String, fmt: String): ByteArray {
        val base = com.transcriptai.app.BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')
        val url = "$base/recordings/$recordingId/export.$fmt"
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Export failed: HTTP ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }
}
