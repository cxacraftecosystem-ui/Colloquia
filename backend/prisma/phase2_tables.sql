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
ALTER TABLE "ta_workspace_members" ADD CONSTRAINT "ta_workspace_members_workspaceId_fkey" FOREIGN KEY ("workspaceId") REFERENCES "ta_workspaces"("id") ON DELETE CASCADE ON UPDATE CASCADE;

