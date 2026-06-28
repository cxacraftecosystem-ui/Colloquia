package com.transcriptai.app.data

import android.content.Context
import android.net.Uri
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
    val prefs = Prefs(appContext)

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

    /** Microsoft / Yahoo authorization-code sign-in: hand the code to the backend for token exchange. */
    suspend fun loginOAuth(provider: String, code: String, redirectUri: String): UserDto {
        val res = api.login(LoginRequest(provider = provider, code = code, redirectUri = redirectUri))
        tokenStore.setToken(res.accessToken)
        tokenStore.setUser(res.user)
        return res.user
    }

    fun logout() = tokenStore.clear()

    // ---------------------------------------------------------------- upload

    /** Upload a recorded file. On any network error the recording is queued to the outbox and the
     *  caller is told it's pending (so nothing is lost offline). Returns the created recording or null
     *  if queued. */
    suspend fun uploadRecording(
        file: File,
        durationSec: Int,
        title: String,
        transcribe: Boolean = true,
        mimeType: String = "audio/m4a",
    ): RecordingDto? {
        return try {
            doUpload(file, durationSec, title, transcribe, mimeType)
        } catch (e: Exception) {
            outbox.add(
                PendingUpload(
                    localId = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    title = title,
                    durationSec = durationSec,
                    mimeType = mimeType,
                )
            )
            null
        }
    }

    private suspend fun doUpload(file: File, durationSec: Int, title: String, transcribe: Boolean, mime: String = "audio/m4a"): RecordingDto {
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
                doUpload(file, item.durationSec, item.title, transcribe = true, mime = item.mimeType)
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

    // ---------------------------------------------------------------- file import

    /** Copy a picked content:// Uri into a cache file we can stream to S3. */
    fun copyUriToCache(uri: Uri, suggestedName: String): File {
        val dir = File(appContext.cacheDir, "imports").apply { mkdirs() }
        val safe = suggestedName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "import.bin" }
        val out = File(dir, "${UUID.randomUUID().toString().take(8)}_$safe")
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open file" }
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    // ---------------------------------------------------------------- OTA "push update to all"

    suspend fun checkLatestRelease(): AppReleaseDto = api.latestRelease()

    /** Master-admin: upload THIS device's installed APK and publish it as the latest release so every
     *  other device can self-update. Returns the published release. */
    suspend fun publishOwnApk(versionCode: Int, versionName: String, notes: String?): AppReleaseDto {
        val apk = File(appContext.applicationInfo.sourceDir)
        val name = "colloquia-$versionName-$versionCode.apk"
        val mime = "application/vnd.android.package-archive"
        val presign = api.presign(PresignRequest(filename = name, mimeType = mime, sizeBytes = apk.length()))
        val body = apk.asRequestBody(mime.toMediaTypeOrNull())
        val putReq = Request.Builder().url(presign.uploadUrl).put(body).header("Content-Type", mime).build()
        http.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("APK upload failed: HTTP ${resp.code}")
        }
        return api.publishRelease(
            AppReleasePublish(
                versionCode = versionCode,
                versionName = versionName,
                objectKey = presign.objectKey,
                url = presign.publicUrl,
                notes = notes,
            )
        )
    }

    /** Download an APK (from the presigned URL) to a cache file for installation. */
    suspend fun downloadApk(url: String): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "colloquia-update.apk")
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Update download failed: HTTP ${resp.code}")
            resp.body?.byteStream()?.use { input -> out.outputStream().use { input.copyTo(it) } }
                ?: throw RuntimeException("Empty download")
        }
        return out
    }

    // ---------------------------------------------------------------- settings

    suspend fun appSettings(): AppSettingsDto = api.getAppSettings()
    suspend fun updateAppSettings(body: AppSettingsUpdate): AppSettingsDto = api.updateAppSettings(body)
}
