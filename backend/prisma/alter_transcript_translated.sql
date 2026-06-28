-- Additive, idempotent column for the "refined + translated to English" conversation view.
-- Runs on every deploy via `prisma db execute` (errors on already-present column are ignored).
ALTER TABLE "ta_transcripts" ADD COLUMN IF NOT EXISTS "translatedText" TEXT;
