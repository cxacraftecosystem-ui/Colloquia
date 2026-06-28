# TranscriptAI

A standalone audio **recording → transcription → AI analysis** app (Otter.ai / Fireflies class), built
to reuse the **same infrastructure and credentials** as the field-repository project: AWS S3 bucket,
OpenAI (Whisper + gpt-4o-mini), Supabase Postgres, and the Google OAuth web client.

- `backend/` — FastAPI + Prisma (Python). Mirrors the proven field-repo patterns: idempotent
  presign→complete S3 uploads, a single-elected async queue worker with rate-limit cooldown, Google
  sign-in, JWT. Its tables live in the **same Supabase database** but are namespaced `ta_*` and use
  `String` status columns (not Prisma enums) so they never collide with the field-repo's tables/types.
- `android/` — Kotlin + Jetpack Compose client (`com.transcriptai.app`).

## What works today (Phase 1)

**Core capture** — Google + email/password sign-in; record with pause/resume/stop; background
recording (foreground service); live timer + level meter; offline outbox (records made offline upload
on reconnect); presign→PUT→idempotent `/complete` upload; library/history with search, sort, filter,
favorites, archive, pin, folders & tags; rename/delete; recently-viewed.

**Transcription** — async Whisper transcription (`verbose_json` → timestamped segments); >24 MB files
chunked & stitched with offset-corrected timestamps; pragmatic speaker labelling (chat-model
diarization); inline segment editing; search within transcript; manual transcript override; admin
re-transcribe; transcript export to **TXT / Markdown / DOCX / PDF**; copy & share.

**AI** — AI titles; four summary types (brief / detailed / bullets / minutes); extraction of action
items, decisions, takeaways, deadlines, entities, keywords, topics; action-item completion tracking;
chat with the transcript; translate / rewrite / tone-conversion / custom prompts.

**Search / analytics / settings** — global search (title + transcript, by speaker/tag/folder); per-user
overview analytics (counts, minutes, action items, AI usage); user settings (dark mode, auto-* toggles,
notifications) + global master-admin transcription mode / off-peak window.

The transcription/analysis pipeline is decoupled from upload: `/complete` enqueues a `TRANSCRIPTION`
job; the elected worker produces the transcript fast, then chains an `ANALYSIS` job for the AI
enrichments. Throttling (HTTP 429/503) is treated as transient — global cooldown + requeue without
consuming an attempt.

## Backend — run locally

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .
# .env already carries the shared credentials. Generate the client + create the ta_* tables:
$env:PATH = "$PWD\.venv\Scripts;$env:PATH"
python -m prisma generate --schema=prisma/schema.prisma
python -m prisma db execute --schema=prisma/schema.prisma --file prisma/create_tables.sql
python scripts/seed_admin.py
python -m uvicorn app.main:app --reload --port 8010
```

Check `http://127.0.0.1:8010/health` and the docs at `http://127.0.0.1:8010/docs`.
(`db execute` only creates the `ta_*` tables; it does not touch the field-repo's tables or the shared
`_prisma_migrations` table. The generated `prisma/create_tables.sql` is the source of truth.)

Local S3 = MinIO (`docker compose up -d` from the field-repo, same `field-repository` bucket). For
real-AWS transcription end-to-end, point `AWS_*` at the live bucket.

## Android — run

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

`local.properties` sets `apiBaseUrl=http://10.0.2.2:8010/api/` (emulator → host). For a device/prod
build point it at the deployed backend. Package `com.transcriptai.app`; Google sign-in reuses the
field-repo's web client id as the Credential Manager `serverClientId` — register this package's debug
SHA-1 as an extra Android OAuth client in the same Google Cloud project if needed.

## Deferred (designed-for, not yet built)

See [DEFERRED.md](DEFERRED.md). Everything below sits behind a clean seam: productivity (Google
Calendar/Tasks/Gmail, reminders, recurring/smart follow-ups); integrations (Slack/Notion/Trello/Jira/
Outlook/Teams/Zoom/Meet); live/real-time transcription & meeting assistant; voice commands; team
collaboration / shared workspaces; meeting templates; semantic / cross-transcript / knowledge-graph
search; AI daily/weekly digests, coaching, conversation analytics; custom automation rules; true
acoustic diarization (pyannote/Deepgram); FCM push (Phase 1 uses local notifications).
