# Feature status

What's implemented vs. what still needs your credentials or a bigger box. (Originally this listed
everything as deferred; most of it is now built — see below.)

## ✅ Implemented (backend live; Android surfaces where noted)
- **True diarization** — Deepgram diarized STT (preferred over Whisper+chat-labelling when `DEEPGRAM_API_KEY` is set). Live.
- **Integrations** — Slack/Teams/Notion/Trello/Jira/webhook connectors + `/integrations` routes + Android **Integrations** screen. Each needs its destination URL/token entered.
- **Productivity** — Google Calendar/Tasks/Gmail routes (client supplies the OAuth access token for the relevant scopes).
- **Collaboration** — workspaces + members + recording shares.
- **Meeting templates** — CRUD + apply-template (runs a prompt set over a transcript).
- **Automation rules** — fired after analysis (push to Slack/Notion/webhook; Google actions are interactive).
- **Knowledge** — semantic + cross-transcript search + knowledge graph + Android **Ask** screen. (Semantic needs `LITE_MODE=false` to index embeddings; otherwise keyword fallback.)
- **Insights** — AI digest (Android: Analytics screen), meeting coaching, conversation analytics, productivity insights.
- **Voice commands** — intent mapping endpoint.
- **Live transcription** — backend WebSocket `/api/live/transcribe` (disabled under `LITE_MODE`).
- **Push (FCM HTTP v1)** — service-account based send on transcript-complete; device-token endpoint; Android Firebase Messaging client wired.
- **Lite mode** — `LITE_MODE` flag for the compute-light vs. heavy split.
- **HTTPS + IPv6** — CloudFront in front of the API.

## ⚠️ Needs your action to fully activate
- **Android FCM client** — add `google-services.json` to `android/app/` + apply the `com.google.gms.google-services` plugin so the app can register device tokens. The dependency + service + registration code are already in place.
- **Android Google sign-in** — register an Android-type OAuth client (package `com.transcriptai.app` + build SHA-1).
- **Each 3rd-party integration** — enter its webhook URL / API token in the Integrations screen (or via `PUT /integrations`).
- **Heavy features on the EC2 box** — flip `LITE_MODE=false` in `BACKEND_ENV` only on a larger instance (the free-tier t3.micro should stay lite).

## ⛔ Still not built (larger scope)
- Live-transcription **Android client UI** (backend WS exists; no streaming recorder screen yet).
- Voice-command **Android UI** (intent endpoint exists; no always-listening client).
- Real-time meeting assistant, personal knowledge-graph visualization UI, AI meeting-coaching scheduling/automation, custom multi-step AI workflows builder UI.
- Zoom/Meet/Teams **meeting bot ingestion** (inbound) — only outbound push is built.

## Architectural seams (so the above slot in cleanly)
- `ProcessingJob.jobType` is a free-form string → new job kinds add without a migration.
- All AI helpers live behind small async functions in `backend/app/services/ai.py`.
- Integration connectors dispatch through `backend/app/services/integrations.py`.
