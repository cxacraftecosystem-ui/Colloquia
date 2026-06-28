-- OTA "push update to all" release registry. Idempotent (IF NOT EXISTS) so it can be re-run on every
-- deploy without erroring. Column names are quoted camelCase to match Prisma's mapping for AppRelease.
CREATE TABLE IF NOT EXISTS "ta_app_releases" (
    "id"            TEXT PRIMARY KEY,
    "versionCode"   INTEGER NOT NULL,
    "versionName"   TEXT NOT NULL,
    "objectKey"     TEXT NOT NULL,
    "url"           TEXT,
    "notes"         TEXT,
    "publishedById" TEXT,
    "publishedAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "ta_app_releases_versionCode_idx" ON "ta_app_releases" ("versionCode");
