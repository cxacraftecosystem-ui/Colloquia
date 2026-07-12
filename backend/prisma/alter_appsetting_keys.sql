-- Runtime provider-key overrides (master-admin can rotate keys without a redeploy). Idempotent.
ALTER TABLE "ta_app_settings" ADD COLUMN IF NOT EXISTS "deepgramApiKey" TEXT;
ALTER TABLE "ta_app_settings" ADD COLUMN IF NOT EXISTS "openaiApiKey" TEXT;
