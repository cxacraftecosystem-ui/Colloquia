package com.transcriptai.app.data

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface TranscriptApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @GET("auth/me")
    suspend fun me(): UserDto

    @PATCH("auth/me")
    suspend fun updateMe(@Body body: ProfileUpdate): UserDto

    @POST("media/presign")
    suspend fun presign(@Body body: PresignRequest): PresignResponse

    @POST("media/complete")
    suspend fun completeUpload(@Body body: CompleteUploadRequest): RecordingDto

    @GET("recordings")
    suspend fun listRecordings(
        @Query("search") search: String? = null,
        @Query("folderId") folderId: String? = null,
        @Query("tagId") tagId: String? = null,
        @Query("favorite") favorite: Boolean? = null,
        @Query("archived") archived: Boolean? = null,
        @Query("pinned") pinned: Boolean? = null,
        @Query("statusFilter") statusFilter: String? = null,
        @Query("sort") sort: String = "recent",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
    ): RecordingPage

    @GET("recordings/{id}")
    suspend fun getRecording(@Path("id") id: String): RecordingDto

    @GET("recordings/{id}/audio-url")
    suspend fun getAudioUrl(@Path("id") id: String): AudioUrlDto

    @PATCH("recordings/{id}")
    suspend fun updateRecording(@Path("id") id: String, @Body body: RecordingUpdate): RecordingDto

    @POST("recordings/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: String): RecordingDto

    @POST("recordings/{id}/archive")
    suspend fun toggleArchive(@Path("id") id: String): RecordingDto

    @POST("recordings/{id}/pin")
    suspend fun togglePin(@Path("id") id: String): RecordingDto

    @PUT("recordings/{id}/tags")
    suspend fun setTags(@Path("id") id: String, @Body body: TagAssign): RecordingDto

    @DELETE("recordings/{id}")
    suspend fun deleteRecording(@Path("id") id: String)

    @GET("recordings/{id}/transcript")
    suspend fun getTranscript(@Path("id") id: String): TranscriptDto

    @PATCH("recordings/{id}/transcript/segments/{idx}")
    suspend fun editSegment(@Path("id") id: String, @Path("idx") idx: Int, @Body body: SegmentEdit): SegmentDto

    @PATCH("recordings/{id}/transcript/speaker")
    suspend fun renameSpeaker(@Path("id") id: String, @Body body: SpeakerRename): TranscriptDto

    @Streaming
    @GET("recordings/{id}/export.{fmt}")
    suspend fun export(@Path("id") id: String, @Path("fmt") fmt: String): ResponseBody

    @POST("recordings/{id}/transcribe-now")
    suspend fun transcribeNow(@Path("id") id: String): Map<String, String?>

    @GET("recordings/{id}/summaries")
    suspend fun listSummaries(@Path("id") id: String): List<SummaryDto>

    @POST("recordings/{id}/summaries/regenerate")
    suspend fun regenerateSummary(@Path("id") id: String, @Body body: SummaryRegenerate): SummaryDto

    @GET("recordings/{id}/action-items")
    suspend fun listActionItems(@Path("id") id: String): List<ActionItemDto>

    @POST("recordings/{id}/action-items/{itemId}/toggle")
    suspend fun toggleActionItem(@Path("id") id: String, @Path("itemId") itemId: String): ActionItemDto

    @GET("recordings/{id}/extracted")
    suspend fun listExtracted(@Path("id") id: String): List<ExtractedItemDto>

    // ---- Google Workspace: push deadlines/action items to Calendar / Tasks ----
    @POST("recordings/{id}/google/calendar")
    suspend fun googleCalendar(@Path("id") id: String, @Body body: GoogleActionRequest): GoogleActionResult

    @POST("recordings/{id}/google/tasks")
    suspend fun googleTasks(@Path("id") id: String, @Body body: GoogleActionRequest): GoogleActionResult

    @GET("recordings/{id}/chat")
    suspend fun chatHistory(@Path("id") id: String): List<ChatMessageDto>

    @POST("recordings/{id}/chat")
    suspend fun chat(@Path("id") id: String, @Body body: ChatRequest): ChatMessageDto

    @POST("recordings/{id}/title")
    suspend fun regenerateTitle(@Path("id") id: String): TransformResult

    @POST("recordings/{id}/translate")
    suspend fun translate(@Path("id") id: String, @Body body: TransformRequest): TransformResult

    @POST("recordings/{id}/rewrite")
    suspend fun rewrite(@Path("id") id: String, @Body body: TransformRequest): TransformResult

    @POST("recordings/{id}/tone")
    suspend fun convertTone(@Path("id") id: String, @Body body: TransformRequest): TransformResult

    @POST("recordings/{id}/custom")
    suspend fun customPrompt(@Path("id") id: String, @Body body: TransformRequest): TransformResult

    @GET("folders")
    suspend fun listFolders(): List<FolderDto>

    @POST("folders")
    suspend fun createFolder(@Body body: FolderCreate): FolderDto

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    @GET("tags")
    suspend fun listTags(): List<TagDto>

    @POST("tags")
    suspend fun createTag(@Body body: TagCreate): TagDto

    @DELETE("tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)

    @GET("analytics/overview")
    suspend fun analyticsOverview(): AnalyticsOverview

    // ---- Phase 2 ----
    @GET("knowledge/search")
    suspend fun knowledgeSearch(@Query("q") q: String): KnowledgeSearchResult

    @POST("knowledge/ask")
    suspend fun knowledgeAsk(@Body body: KnowledgeQuery): TransformResult

    @GET("knowledge/graph")
    suspend fun knowledgeGraph(): Map<String, kotlinx.serialization.json.JsonElement>

    @GET("insights/digest")
    suspend fun digest(@Query("period") period: String = "weekly"): TransformResult

    @GET("recordings/{id}/coaching")
    suspend fun coaching(@Path("id") id: String): TransformResult

    @GET("integrations")
    suspend fun listIntegrations(): List<IntegrationDto>

    @PUT("integrations")
    suspend fun upsertIntegration(@Body body: IntegrationUpsert): IntegrationDto

    @DELETE("integrations/{provider}")
    suspend fun deleteIntegration(@Path("provider") provider: String)

    @POST("integrations/push/{id}")
    suspend fun pushToProvider(@Path("id") id: String, @Body body: Map<String, String>): Map<String, String?>

    @POST("voice/command")
    suspend fun voiceCommand(@Body body: VoiceCommand): Map<String, kotlinx.serialization.json.JsonElement>

    @POST("devices")
    suspend fun registerDevice(@Body body: Map<String, String>): Map<String, kotlinx.serialization.json.JsonElement>

    // ---- Settings (global transcription mode is master-admin; /me is per-user) ----
    @GET("settings")
    suspend fun getAppSettings(): AppSettingsDto

    @PUT("settings")
    suspend fun updateAppSettings(@Body body: AppSettingsUpdate): AppSettingsDto

    @GET("settings/me")
    suspend fun getMySettings(): Map<String, kotlinx.serialization.json.JsonElement>

    @PUT("settings/me")
    suspend fun updateMySettings(@Body body: Map<String, kotlinx.serialization.json.JsonElement>): Map<String, kotlinx.serialization.json.JsonElement>

    // ---- OTA "push update to all" ----
    @GET("app/release/latest")
    suspend fun latestRelease(): AppReleaseDto

    @POST("app/release")
    suspend fun publishRelease(@Body body: AppReleasePublish): AppReleaseDto
}
