package com.transcriptai.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriptai.app.data.*
import com.transcriptai.app.recording.AudioExtract
import com.transcriptai.app.recording.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException

/** Turn raw network/HTTP exceptions into plain-language messages a non-technical user can act on. */
fun humanError(e: Throwable, fallback: String = "Something went wrong. Please try again."): String = when (e) {
    is HttpException -> when (e.code()) {
        401 -> "Your session expired. Please sign in again."
        403 -> "You don't have access to that."
        404 -> "We couldn't find that."
        429 -> "Too many requests right now — please wait a moment and try again."
        in 500..599 -> "Our server had a hiccup. Please try again in a moment."
        else -> fallback
    }
    is IOException -> "You appear to be offline. Check your connection and try again."
    else -> e.message?.takeIf { it.isNotBlank() } ?: fallback
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app.applicationContext
    val repo = Repository(app)

    var user by mutableStateOf<UserDto?>(repo.cachedUser())
        private set
    val isLoggedIn: Boolean get() = repo.isLoggedIn && user != null

    var recordings by mutableStateOf<List<RecordingDto>>(emptyList())
        private set
    var folders by mutableStateOf<List<FolderDto>>(emptyList())
        private set
    var tags by mutableStateOf<List<TagDto>>(emptyList())
        private set
    var detail by mutableStateOf<RecordingDto?>(null)
        private set
    var overview by mutableStateOf<AnalyticsOverview?>(null)
        private set

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var pendingUploads by mutableStateOf(0)

    // Filters
    var search by mutableStateOf("")
    var sort by mutableStateOf("recent")
    var showArchived by mutableStateOf(false)
    var favoritesOnly by mutableStateOf(false)
    var activeFolderId by mutableStateOf<String?>(null)

    // Settings (device-local prefs + global transcription mode)
    var darkMode by mutableStateOf(repo.prefs.darkMode)
    var noiseCancellation by mutableStateOf(repo.prefs.noiseCancellation)
        private set
    var highQuality by mutableStateOf(repo.prefs.highQuality)
        private set
    var autoUpload by mutableStateOf(repo.prefs.autoUpload)
        private set
    var notifications by mutableStateOf(repo.prefs.notifications)
        private set
    var showTimestamps by mutableStateOf(repo.prefs.showTimestamps)
        private set
    var transcriptionMode by mutableStateOf("REFINED")
        private set

    val isMasterAdmin: Boolean get() = user?.role == "MASTER_ADMIN"

    // OTA
    var updateRelease by mutableStateOf<AppReleaseDto?>(null)   // non-null => a newer version exists
    var updateBusy by mutableStateOf(false)
    var publishingUpdate by mutableStateOf(false)

    private inline fun io(crossinline block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            error = humanError(e)
        }
    }

    fun clearError() { error = null }

    // ---------------------------------------------------------------- auth
    fun login(email: String, password: String, onDone: (Boolean) -> Unit) = io {
        loading = true
        try {
            user = repo.login(email.trim(), password)
            onDone(true)
            refreshAll()
        } catch (e: Exception) {
            error = if (e is HttpException && e.code() == 401) "Incorrect email or password." else humanError(e, "Login failed.")
            onDone(false)
        } finally { loading = false }
    }

    fun loginGoogle(idToken: String, onDone: (Boolean) -> Unit) = io {
        loading = true
        try {
            user = repo.loginGoogle(idToken)
            onDone(true)
            refreshAll()
        } catch (e: Exception) {
            error = humanError(e, "Google sign-in failed."); onDone(false)
        } finally { loading = false }
    }

    /** Microsoft / Yahoo sign-in completion (after the browser returns an authorization code). */
    fun loginOAuth(provider: String, code: String, onDone: (Boolean) -> Unit) = io {
        loading = true
        try {
            user = repo.loginOAuth(provider, code, com.transcriptai.app.data.OAuthClient.redirectUri)
            onDone(true)
            refreshAll()
        } catch (e: Exception) {
            error = humanError(e, "${provider.lowercase().replaceFirstChar { it.uppercase() }} sign-in failed."); onDone(false)
        } finally { loading = false }
    }

    fun logout() {
        repo.logout(); user = null; recordings = emptyList(); detail = null
    }

    // ---------------------------------------------------------------- library
    fun refreshAll() = io {
        refreshRecordings()
        folders = repo.api.listFolders()
        tags = repo.api.listTags()
        overview = repo.api.analyticsOverview()
        pendingUploads = repo.pendingCount()
    }

    fun refreshRecordings() = io {
        loading = true
        try {
            recordings = repo.api.listRecordings(
                search = search.ifBlank { null },
                folderId = activeFolderId,
                favorite = if (favoritesOnly) true else null,
                archived = if (showArchived) true else null,
                sort = sort,
            ).items
        } finally { loading = false }
    }

    fun loadDetail(id: String) = io {
        detail = repo.api.getRecording(id)
    }

    fun toggleFavorite(id: String) = io { repo.api.toggleFavorite(id); refreshAfterMutation(id) }
    fun toggleArchive(id: String) = io { repo.api.toggleArchive(id); refreshRecordings() }
    fun togglePin(id: String) = io { repo.api.togglePin(id); refreshAfterMutation(id) }
    fun rename(id: String, title: String) = io { repo.api.updateRecording(id, RecordingUpdate(title = title)); refreshAfterMutation(id) }
    fun moveToFolder(id: String, folderId: String?) = io { repo.api.updateRecording(id, RecordingUpdate(folderId = folderId)); refreshAfterMutation(id) }
    fun delete(id: String) = io { repo.api.deleteRecording(id); detail = null; refreshRecordings() }

    private suspend fun refreshAfterMutation(id: String) {
        runCatching { detail = repo.api.getRecording(id) }
        recordings = repo.api.listRecordings(
            search = search.ifBlank { null }, folderId = activeFolderId,
            favorite = if (favoritesOnly) true else null, archived = if (showArchived) true else null, sort = sort,
        ).items
    }

    fun transcribeNow(id: String) = io { repo.api.transcribeNow(id); loadDetail(id) }

    // ---------------------------------------------------------------- folders / tags
    fun createFolder(name: String, color: String?) = io { repo.api.createFolder(FolderCreate(name, color)); folders = repo.api.listFolders() }
    fun deleteFolder(id: String) = io { repo.api.deleteFolder(id); folders = repo.api.listFolders(); refreshRecordings() }
    fun createTag(name: String) = io { repo.api.createTag(TagCreate(name)); tags = repo.api.listTags() }
    fun deleteTag(id: String) = io { repo.api.deleteTag(id); tags = repo.api.listTags() }
    fun setTags(id: String, tagIds: List<String>) = io { repo.api.setTags(id, TagAssign(tagIds)); loadDetail(id) }

    // ---------------------------------------------------------------- AI
    fun regenerateSummary(id: String, type: String, onResult: (String?) -> Unit) = io {
        val s = repo.api.regenerateSummary(id, SummaryRegenerate(type)); loadDetail(id); onResult(s.content)
    }
    fun toggleActionItem(id: String, itemId: String) = io { repo.api.toggleActionItem(id, itemId); loadDetail(id) }
    fun regenerateTitle(id: String) = io { repo.api.regenerateTitle(id); loadDetail(id) }
    fun editSegment(id: String, idx: Int, text: String) = io { repo.api.editSegment(id, idx, SegmentEdit(text)); loadDetail(id) }
    /** Apply one or more speaker renames sequentially (avoids racing transcript updates), then reload. */
    fun renameSpeakers(id: String, renames: Map<String, String>) = io {
        for ((old, new) in renames) {
            if (new.isNotBlank() && new.trim() != old.trim()) {
                repo.api.renameSpeaker(id, SpeakerRename(old.trim(), new.trim()))
            }
        }
        loadDetail(id)
    }

    fun transform(id: String, kind: String, arg: String?, onResult: (TransformResult) -> Unit) = io {
        val res = when (kind) {
            "translate" -> repo.api.translate(id, TransformRequest(targetLanguage = arg ?: "English"))
            "rewrite" -> repo.api.rewrite(id, TransformRequest(instruction = arg))
            "tone" -> repo.api.convertTone(id, TransformRequest(tone = arg ?: "professional"))
            else -> repo.api.customPrompt(id, TransformRequest(instruction = arg))
        }
        onResult(res)
    }

    var chatMessages by mutableStateOf<List<ChatMessageDto>>(emptyList())
        private set
    fun loadChat(id: String) = io { chatMessages = repo.api.chatHistory(id) }
    fun sendChat(id: String, q: String) = io {
        chatMessages = chatMessages + ChatMessageDto(role = "USER", content = q)
        val reply = repo.api.chat(id, ChatRequest(q))
        chatMessages = repo.api.chatHistory(id)
        if (reply.content.isBlank()) error = "No answer"
    }

    // ---------------------------------------------------------------- upload
    fun finishRecordingAndUpload(file: File, durationSec: Int, title: String) = io {
        loading = true
        try {
            val created = repo.uploadRecording(file, durationSec, title)
            pendingUploads = repo.pendingCount()
            if (created != null) {
                Notifications.notify(appCtx, file.hashCode(), "Uploaded", "“$title” is transcribing…")
                refreshRecordings()
            } else {
                Notifications.notify(appCtx, file.hashCode(), "Saved offline", "“$title” will upload when online")
            }
        } finally { loading = false }
    }

    fun flushOutbox() = io {
        val n = withContext(Dispatchers.IO) { repo.flushOutbox() }
        pendingUploads = repo.pendingCount()
        if (n > 0) refreshRecordings()
    }

    /** Register this device's FCM token for push. No-op until google-services.json is added (FCM
     *  can't initialize without it); failures are swallowed so they never disrupt the UI. */
    fun registerPushToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    if (!token.isNullOrBlank()) {
                        viewModelScope.launch {
                            runCatching { repo.api.registerDevice(mapOf("token" to token, "platform" to "android")) }
                        }
                    }
                }
        } catch (_: Exception) {
            // Firebase not configured (no google-services.json) — ignore.
        }
    }

    // ---------------------------------------------------------------- phase 2
    var askAnswer by mutableStateOf<String?>(null)
    var searchHits by mutableStateOf<List<KnowledgeHit>>(emptyList())
    var integrations by mutableStateOf<List<IntegrationDto>>(emptyList())
    var digestContent by mutableStateOf<String?>(null)

    fun ask(query: String) = io {
        loading = true
        try {
            askAnswer = repo.api.knowledgeAsk(KnowledgeQuery(query)).answer ?: "(no answer)"
            searchHits = repo.api.knowledgeSearch(query).results
        } finally { loading = false }
    }

    fun loadIntegrations() = io { integrations = repo.api.listIntegrations() }
    fun connectIntegration(provider: String, config: Map<String, String>) = io {
        repo.api.upsertIntegration(IntegrationUpsert(provider, config, true)); integrations = repo.api.listIntegrations()
    }
    fun disconnectIntegration(provider: String) = io { repo.api.deleteIntegration(provider); integrations = repo.api.listIntegrations() }
    fun pushRecording(id: String, provider: String, onDone: (String) -> Unit) = io {
        val res = repo.api.pushToProvider(id, mapOf("provider" to provider)); onDone(res["status"] ?: "?")
    }
    fun loadDigest(period: String) = io { loading = true; try { digestContent = repo.api.digest(period).content } finally { loading = false } }
    fun coaching(id: String, onDone: (String?) -> Unit) = io { onDone(repo.api.coaching(id).content) }

    // ---------------------------------------------------------------- settings
    fun saveDarkMode(value: Boolean?) {
        darkMode = value
        repo.prefs.darkMode = value
    }

    fun saveNoiseCancellation(v: Boolean) { noiseCancellation = v; repo.prefs.noiseCancellation = v }
    fun saveHighQuality(v: Boolean) { highQuality = v; repo.prefs.highQuality = v }
    fun saveAutoUpload(v: Boolean) { autoUpload = v; repo.prefs.autoUpload = v }
    fun saveNotifications(v: Boolean) { notifications = v; repo.prefs.notifications = v }
    fun saveShowTimestamps(v: Boolean) { showTimestamps = v; repo.prefs.showTimestamps = v }

    fun loadAppSettings() = io { transcriptionMode = repo.appSettings().transcriptionMode }

    fun chooseTranscriptionMode(mode: String) = io {
        // Global (master-admin only); optimistic update with server confirmation.
        transcriptionMode = mode
        transcriptionMode = repo.updateAppSettings(AppSettingsUpdate(transcriptionMode = mode)).transcriptionMode
    }

    // ---------------------------------------------------------------- file import (audio / video)
    /** Import a picked audio or video file. For video, the audio track is extracted on-device so only
     *  the audio is uploaded & transcribed. */
    fun importMedia(uri: Uri, displayName: String, isVideo: Boolean, title: String) = io {
        loading = true
        try {
            val source = withContext(Dispatchers.IO) { repo.copyUriToCache(uri, displayName) }
            val toUpload = if (isVideo) {
                withContext(Dispatchers.IO) {
                    AudioExtract.extractAudio(source, File(appCtx.cacheDir, "imports")).also { source.delete() }
                }
            } else source
            val created = repo.uploadRecording(toUpload, durationSec = 0, title = title, mimeType = "audio/m4a")
            pendingUploads = repo.pendingCount()
            if (created != null) {
                Notifications.notify(appCtx, toUpload.hashCode(), "Uploaded", "“$title” is transcribing…")
                refreshRecordings()
            } else {
                Notifications.notify(appCtx, toUpload.hashCode(), "Saved offline", "“$title” will upload when online")
            }
        } catch (e: Exception) {
            error = humanError(e, "Couldn't import that file.")
        } finally { loading = false }
    }

    // ---------------------------------------------------------------- OTA "push update to all"
    fun checkForUpdate(silent: Boolean = true) = io {
        try {
            val latest = repo.checkLatestRelease()
            updateRelease = if (latest.versionCode > BuildConfig.VERSION_CODE && !latest.downloadUrl.isNullOrBlank()) latest else null
        } catch (e: Exception) {
            if (!silent) error = humanError(e, "Couldn't check for updates.")
        }
    }

    fun downloadAndInstallUpdate() = io {
        val rel = updateRelease ?: return@io
        val url = rel.downloadUrl ?: rel.url ?: return@io
        updateBusy = true
        try {
            val apk = withContext(Dispatchers.IO) { repo.downloadApk(url) }
            installApk(apk)
        } catch (e: Exception) {
            error = humanError(e, "Update download failed.")
        } finally { updateBusy = false }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appCtx.startActivity(intent)
    }

    /** Master-admin: publish THIS build to every device. */
    fun pushUpdateToAll(notes: String?, onDone: (Boolean) -> Unit) = io {
        publishingUpdate = true
        try {
            repo.publishOwnApk(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, notes)
            onDone(true)
        } catch (e: Exception) {
            error = humanError(e, "Couldn't publish the update."); onDone(false)
        } finally { publishingUpdate = false }
    }
}
