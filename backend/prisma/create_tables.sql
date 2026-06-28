-- CreateTable
CREATE TABLE "ta_users" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "passwordHash" TEXT,
    "avatarUrl" TEXT,
    "role" TEXT NOT NULL DEFAULT 'USER',
    "authProvider" TEXT NOT NULL DEFAULT 'LOCAL',
    "settingsJson" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_folders" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "color" TEXT,
    "parentId" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_folders_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_tags" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_tags_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_recordings" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "aiTitle" TEXT,
    "language" TEXT,
    "objectKey" TEXT NOT NULL,
    "bucket" TEXT,
    "url" TEXT,
    "mimeType" TEXT,
    "originalFilename" TEXT,
    "sizeBytes" INTEGER,
    "durationSec" INTEGER,
    "status" TEXT NOT NULL DEFAULT 'UPLOADING',
    "transcriptStatus" TEXT NOT NULL DEFAULT 'PENDING',
    "transcriptError" TEXT,
    "isFavorite" BOOLEAN NOT NULL DEFAULT false,
    "isArchived" BOOLEAN NOT NULL DEFAULT false,
    "isPinned" BOOLEAN NOT NULL DEFAULT false,
    "folderId" TEXT,
    "recordedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastViewedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_recordings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_recording_tags" (
    "recordingId" TEXT NOT NULL,
    "tagId" TEXT NOT NULL,

    CONSTRAINT "ta_recording_tags_pkey" PRIMARY KEY ("recordingId","tagId")
);

-- CreateTable
CREATE TABLE "ta_transcripts" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'PENDING',
    "rawText" TEXT,
    "refinedText" TEXT,
    "language" TEXT,
    "model" TEXT,
    "durationMs" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_transcripts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_transcript_segments" (
    "id" TEXT NOT NULL,
    "transcriptId" TEXT NOT NULL,
    "idx" INTEGER NOT NULL,
    "speaker" TEXT,
    "startMs" INTEGER NOT NULL,
    "endMs" INTEGER NOT NULL,
    "text" TEXT NOT NULL,
    "editedText" TEXT,

    CONSTRAINT "ta_transcript_segments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_summaries" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "model" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_summaries_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_action_items" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "kind" TEXT NOT NULL DEFAULT 'ACTION',
    "text" TEXT NOT NULL,
    "assignee" TEXT,
    "dueDate" TIMESTAMP(3),
    "done" BOOLEAN NOT NULL DEFAULT false,
    "sourceSegmentIdx" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_action_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_extracted_items" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "kind" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "score" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_extracted_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_chat_messages" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "role" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_chat_messages_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_processing_jobs" (
    "id" TEXT NOT NULL,
    "jobType" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'QUEUED',
    "priority" INTEGER NOT NULL DEFAULT 50,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "maxAttempts" INTEGER NOT NULL DEFAULT 3,
    "runAfter" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lockedAt" TIMESTAMP(3),
    "lockedBy" TEXT,
    "startedAt" TIMESTAMP(3),
    "completedAt" TIMESTAMP(3),
    "error" TEXT,
    "result" JSONB,
    "recordingId" TEXT NOT NULL,
    "requestedById" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_processing_jobs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_app_settings" (
    "id" TEXT NOT NULL DEFAULT 'singleton',
    "transcriptionMode" TEXT NOT NULL DEFAULT 'REFINED',
    "defaultLanguage" TEXT,
    "batchWindowEnabled" BOOLEAN NOT NULL DEFAULT false,
    "batchWindowStart" TEXT NOT NULL DEFAULT '02:00',
    "batchWindowEnd" TEXT NOT NULL DEFAULT '05:00',
    "batchTimezone" TEXT NOT NULL DEFAULT 'Asia/Kolkata',
    "updatedById" TEXT,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_app_settings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_integrations" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "provider" TEXT NOT NULL,
    "config" JSONB,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_integrations_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_workspaces" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_workspaces_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_workspace_members" (
    "id" TEXT NOT NULL,
    "workspaceId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "role" TEXT NOT NULL DEFAULT 'VIEWER',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_workspace_members_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_recording_shares" (
    "id" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "ownerId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tier" TEXT NOT NULL DEFAULT 'VIEW',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_recording_shares_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_meeting_templates" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "prompts" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_meeting_templates_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_automation_rules" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "trigger" TEXT NOT NULL DEFAULT 'ON_ANALYZED',
    "action" TEXT NOT NULL,
    "config" JSONB,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "ta_automation_rules_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_embeddings" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "recordingId" TEXT NOT NULL,
    "segmentIdx" INTEGER,
    "text" TEXT NOT NULL,
    "vector" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_embeddings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_device_tokens" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "token" TEXT NOT NULL,
    "platform" TEXT NOT NULL DEFAULT 'android',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_device_tokens_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ta_digests" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "content" TEXT NOT NULL,
    "periodStart" TIMESTAMP(3) NOT NULL,
    "periodEnd" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ta_digests_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "ta_users_email_key" ON "ta_users"("email");

-- CreateIndex
CREATE INDEX "ta_folders_userId_idx" ON "ta_folders"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ta_tags_userId_name_key" ON "ta_tags"("userId", "name");

-- CreateIndex
CREATE UNIQUE INDEX "ta_recordings_objectKey_key" ON "ta_recordings"("objectKey");

-- CreateIndex
CREATE INDEX "ta_recordings_userId_createdAt_idx" ON "ta_recordings"("userId", "createdAt");

-- CreateIndex
CREATE INDEX "ta_recordings_userId_isArchived_isFavorite_idx" ON "ta_recordings"("userId", "isArchived", "isFavorite");

-- CreateIndex
CREATE UNIQUE INDEX "ta_transcripts_recordingId_key" ON "ta_transcripts"("recordingId");

-- CreateIndex
CREATE INDEX "ta_transcript_segments_transcriptId_idx_idx" ON "ta_transcript_segments"("transcriptId", "idx");

-- CreateIndex
CREATE UNIQUE INDEX "ta_summaries_recordingId_type_key" ON "ta_summaries"("recordingId", "type");

-- CreateIndex
CREATE INDEX "ta_action_items_recordingId_kind_idx" ON "ta_action_items"("recordingId", "kind");

-- CreateIndex
CREATE INDEX "ta_extracted_items_recordingId_kind_idx" ON "ta_extracted_items"("recordingId", "kind");

-- CreateIndex
CREATE INDEX "ta_chat_messages_recordingId_createdAt_idx" ON "ta_chat_messages"("recordingId", "createdAt");

-- CreateIndex
CREATE INDEX "ta_processing_jobs_status_runAfter_priority_createdAt_idx" ON "ta_processing_jobs"("status", "runAfter", "priority", "createdAt");

-- CreateIndex
CREATE INDEX "ta_integrations_userId_idx" ON "ta_integrations"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ta_integrations_userId_provider_key" ON "ta_integrations"("userId", "provider");

-- CreateIndex
CREATE INDEX "ta_workspaces_ownerId_idx" ON "ta_workspaces"("ownerId");

-- CreateIndex
CREATE INDEX "ta_workspace_members_userId_idx" ON "ta_workspace_members"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ta_workspace_members_workspaceId_userId_key" ON "ta_workspace_members"("workspaceId", "userId");

-- CreateIndex
CREATE INDEX "ta_recording_shares_userId_idx" ON "ta_recording_shares"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ta_recording_shares_recordingId_userId_key" ON "ta_recording_shares"("recordingId", "userId");

-- CreateIndex
CREATE INDEX "ta_meeting_templates_userId_idx" ON "ta_meeting_templates"("userId");

-- CreateIndex
CREATE INDEX "ta_automation_rules_userId_enabled_idx" ON "ta_automation_rules"("userId", "enabled");

-- CreateIndex
CREATE INDEX "ta_embeddings_userId_idx" ON "ta_embeddings"("userId");

-- CreateIndex
CREATE INDEX "ta_embeddings_recordingId_idx" ON "ta_embeddings"("recordingId");

-- CreateIndex
CREATE UNIQUE INDEX "ta_device_tokens_token_key" ON "ta_device_tokens"("token");

-- CreateIndex
CREATE INDEX "ta_device_tokens_userId_idx" ON "ta_device_tokens"("userId");

-- CreateIndex
CREATE INDEX "ta_digests_userId_type_idx" ON "ta_digests"("userId", "type");

-- AddForeignKey
ALTER TABLE "ta_folders" ADD CONSTRAINT "ta_folders_userId_fkey" FOREIGN KEY ("userId") REFERENCES "ta_users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_folders" ADD CONSTRAINT "ta_folders_parentId_fkey" FOREIGN KEY ("parentId") REFERENCES "ta_folders"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_tags" ADD CONSTRAINT "ta_tags_userId_fkey" FOREIGN KEY ("userId") REFERENCES "ta_users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_recordings" ADD CONSTRAINT "ta_recordings_userId_fkey" FOREIGN KEY ("userId") REFERENCES "ta_users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_recordings" ADD CONSTRAINT "ta_recordings_folderId_fkey" FOREIGN KEY ("folderId") REFERENCES "ta_folders"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_recording_tags" ADD CONSTRAINT "ta_recording_tags_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_recording_tags" ADD CONSTRAINT "ta_recording_tags_tagId_fkey" FOREIGN KEY ("tagId") REFERENCES "ta_tags"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_transcripts" ADD CONSTRAINT "ta_transcripts_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_transcript_segments" ADD CONSTRAINT "ta_transcript_segments_transcriptId_fkey" FOREIGN KEY ("transcriptId") REFERENCES "ta_transcripts"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_summaries" ADD CONSTRAINT "ta_summaries_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_action_items" ADD CONSTRAINT "ta_action_items_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_extracted_items" ADD CONSTRAINT "ta_extracted_items_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_chat_messages" ADD CONSTRAINT "ta_chat_messages_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_processing_jobs" ADD CONSTRAINT "ta_processing_jobs_recordingId_fkey" FOREIGN KEY ("recordingId") REFERENCES "ta_recordings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_processing_jobs" ADD CONSTRAINT "ta_processing_jobs_requestedById_fkey" FOREIGN KEY ("requestedById") REFERENCES "ta_users"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ta_workspace_members" ADD CONSTRAINT "ta_workspace_members_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "ta_workspaces"("id") ON DELETE CASCADE ON UPDATE CASCADE;

