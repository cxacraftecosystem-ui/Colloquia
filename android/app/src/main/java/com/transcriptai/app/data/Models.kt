package com.transcriptai.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: String = "USER",
    val authProvider: String = "LOCAL",
    val settingsJson: JsonObject? = null,
)

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val googleIdToken: String? = null,
    val provider: String? = null,
    val code: String? = null,
    val redirectUri: String? = null,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "bearer",
    val user: UserDto,
)

@Serializable
data class ProfileUpdate(
    val name: String? = null,
    val avatarUrl: String? = null,
    val settingsJson: JsonObject? = null,
)

@Serializable
data class TagDto(val id: String, val name: String)

@Serializable
data class RecordingTagDto(val tag: TagDto? = null)

@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val parentId: String? = null,
)

// ---- OTA app releases ----
@Serializable
data class AppReleaseDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String? = null,
    val downloadUrl: String? = null,
    val notes: String? = null,
    val publishedAt: String? = null,
)

@Serializable
data class AppReleasePublish(
    val versionCode: Int,
    val versionName: String,
    val objectKey: String,
    val url: String? = null,
    val notes: String? = null,
)

// ---- Settings ----
@Serializable
data class AppSettingsDto(
    val transcriptionMode: String = "REFINED_TRANSLATED",
    val defaultLanguage: String? = null,
)

@Serializable
data class AppSettingsUpdate(
    val transcriptionMode: String? = null,
    val defaultLanguage: String? = null,
)

@Serializable
data class AudioUrlDto(val url: String, val expiresIn: Int = 3600)

@Serializable
data class SegmentDto(
    val idx: Int,
    val speaker: String? = null,
    val startMs: Int,
    val endMs: Int,
    val text: String,
    val editedText: String? = null,
)

@Serializable
data class TranscriptDto(
    val status: String = "PENDING",
    val rawText: String? = null,
    val refinedText: String? = null,
    // Refined dialogue translated to English (set when the source wasn't already English).
    val translatedText: String? = null,
    val language: String? = null,
    val durationMs: Int? = null,
    val segments: List<SegmentDto> = emptyList(),
)

@Serializable
data class SummaryDto(
    val id: String? = null,
    val type: String = "BRIEF",
    val content: String = "",
)

@Serializable
data class ActionItemDto(
    val id: String,
    val kind: String = "ACTION",
    val text: String,
    val assignee: String? = null,
    val dueDate: String? = null,
    val done: Boolean = false,
)

@Serializable
data class ExtractedItemDto(val kind: String, val value: String)

@Serializable
data class ChatMessageDto(
    val id: String? = null,
    val role: String,
    val content: String,
    val createdAt: String? = null,
)

@Serializable
data class RecordingDto(
    val id: String,
    val title: String,
    val aiTitle: String? = null,
    val language: String? = null,
    val objectKey: String? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val durationSec: Int? = null,
    val status: String = "UPLOADED",
    val transcriptStatus: String = "PENDING",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val folderId: String? = null,
    val recordedAt: String? = null,
    val createdAt: String? = null,
    val folder: FolderDto? = null,
    val tags: List<RecordingTagDto> = emptyList(),
    val transcript: TranscriptDto? = null,
    val summaries: List<SummaryDto> = emptyList(),
    val actionItems: List<ActionItemDto> = emptyList(),
    val extractedItems: List<ExtractedItemDto> = emptyList(),
)

@Serializable
data class RecordingPage(
    val items: List<RecordingDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val pages: Int = 0,
)

@Serializable
data class PresignRequest(val filename: String, val mimeType: String, val sizeBytes: Long? = null)

@Serializable
data class PresignResponse(
    val uploadUrl: String,
    val method: String = "PUT",
    val objectKey: String,
    val bucket: String,
    val headers: Map<String, String> = emptyMap(),
    val publicUrl: String? = null,
)

@Serializable
data class CompleteUploadRequest(
    val objectKey: String,
    val bucket: String? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val originalFilename: String? = null,
    val sizeBytes: Long? = null,
    val durationSec: Int? = null,
    val title: String? = null,
    val folderId: String? = null,
    val recordedAt: String? = null,
    val transcribe: Boolean = true,
)

@Serializable
data class FolderCreate(val name: String, val color: String? = null, val parentId: String? = null)

@Serializable
data class TagCreate(val name: String)

@Serializable
data class TagAssign(val tagIds: List<String>)

@Serializable
data class RecordingUpdate(
    val title: String? = null,
    val folderId: String? = null,
    val language: String? = null,
)

@Serializable
data class SegmentEdit(val text: String)

@Serializable
data class SpeakerRename(val old: String, val new: String)

@Serializable
data class ChatRequest(val question: String)

@Serializable
data class TransformRequest(
    val targetLanguage: String? = null,
    val tone: String? = null,
    val instruction: String? = null,
)

@Serializable
data class SummaryRegenerate(val type: String)

@Serializable
data class AnalyticsOverview(
    val totalRecordings: Int = 0,
    val totalDurationSec: Int = 0,
    val transcribedRecordings: Int = 0,
    val favorites: Int = 0,
    val actionItemsTotal: Int = 0,
    val actionItemsCompleted: Int = 0,
    val aiJobsCompleted: Int = 0,
)

@Serializable
data class TransformResult(
    val status: String = "FAILED",
    val result: String? = null,
    val answer: String? = null,
    val title: String? = null,
    val content: String? = null,
    val message: String? = null,
)

@Serializable
data class KnowledgeHit(
    val recordingId: String? = null,
    val segmentIdx: Int? = null,
    val text: String,
    val score: Double? = null,
)

@Serializable
data class KnowledgeSearchResult(
    val mode: String = "keyword",
    val results: List<KnowledgeHit> = emptyList(),
)

@Serializable
data class IntegrationDto(
    val id: String? = null,
    val provider: String,
    val enabled: Boolean = true,
)

@Serializable
data class IntegrationUpsert(
    val provider: String,
    val config: Map<String, String>? = null,
    val enabled: Boolean = true,
)

@Serializable
data class KnowledgeQuery(val query: String)

@Serializable
data class VoiceCommand(val command: String)

// ---- Google Workspace push (Calendar / Tasks) ----
@Serializable
data class GoogleActionRequest(
    val googleAccessToken: String? = null,
    val to: String? = null,
)

@Serializable
data class GoogleActionResult(
    val status: String = "NEEDS_AUTH",
    val scope: String? = null,
    val created: List<JsonObject> = emptyList(),
)
