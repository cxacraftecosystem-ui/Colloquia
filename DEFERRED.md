# Deferred features & how they slot in

Phase 1 delivered the full record → transcribe → AI-analysis product. The remaining items from the
original brief are deferred but the architecture leaves a clean seam for each. Grouped by what they
need.

## Need 3rd-party OAuth credentials (you must supply an app/client)
- **Google Calendar event suggestions, Google Tasks, Gmail drafts** — extend the existing Google
  sign-in to request the relevant scopes; backend gains a `productivity` service + routes. First to do,
  since we already own a Google OAuth client.
- **Reminder suggestions, follow-up reminders, recurring-task detection, smart follow-ups** — derive
  from the already-extracted action items/deadlines; surface as Calendar/Tasks once the above lands.
- **Integrations: Slack, Notion, Trello, Jira, Outlook, Teams, Zoom, Google Meet** — each is an
  outbound connector behind a common `Integration` interface (OAuth creds per provider). Zoom/Meet/
  Teams also feed the live-transcription path below.

## Need extra infra / heavier compute
- **True acoustic diarization** — replace `assign_speakers_to_segments` (chat-model labelling) with
  pyannote or a provider (Deepgram/AssemblyAI). Same function signature; segments already carry
  speaker fields.
- **Live / real-time transcription + real-time meeting assistant + voice commands** — streaming STT
  (WebSocket) instead of the batch Whisper job; new `live` channel on the backend and a streaming
  recorder on Android.
- **Semantic search, cross-transcript querying, personal knowledge graph** — add an `embedding`
  column (pgvector) populated in the ANALYSIS job; the search route already isolates the query layer.
- **AI daily digest / weekly recap / productivity insights / meeting coaching / conversation
  analytics** — scheduled jobs over existing transcripts/summaries/action-items (cron worker).

## Mostly client/product work
- **Team collaboration / shared workspaces / shared recordings** — add an org/membership model and a
  share-grant table (the field-repo's DataAccessGrant pattern is a proven template).
- **Meeting templates, custom AI workflows, custom automation rules** — a user-defined prompt/automation
  store driving the existing transform endpoints.
- **FCM push** — Phase 1 uses local notifications via WorkManager polling; swap in FCM for
  processing-complete/reminder pushes.

## Notes
- The backend `ProcessingJob.jobType` is a free-form string, so new job kinds (DIARIZATION, EMBEDDING,
  DIGEST, …) add without a migration.
- All AI helpers live in `backend/app/services/ai.py` behind small async functions — new capabilities
  (translate-to, custom workflow steps) are additive there.
