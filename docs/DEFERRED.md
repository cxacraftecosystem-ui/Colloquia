<picture>
  <source media="(prefers-color-scheme: dark)" srcset="readme-kit/assets/divider-dark.svg">
  <img src="readme-kit/assets/divider-light.svg" width="100%" alt="">
</picture>

# <img src="readme-kit/assets/icons/badge-check.svg" width="32" align="top" alt="">&nbsp; Feature status

What is built, on which side, and what still needs a credential, a console step or a bigger box. This
page first listed everything as deferred; most of it is now built.

| Section | What it covers |
|---|---|
| [Built](#built) | every feature, with where it is surfaced |
| [Needs your action](#needs-your-action) | the steps outside the code |
| [Still not built](#still-not-built) | the larger pieces still to come |
| [Seams](#seams) | where those pieces slot in |

## Built

**API** means the backend route exists; **Android** means the app has a screen or button for it.

| Feature | API | Android | Notes |
|---|:---:|:---:|---|
| Record with pause, resume and background recording | · | ✓ | a foreground service of type microphone, with a live timer and level meter |
| Import audio or video | ✓ | ✓ | a video's audio track is copied out on the phone, without re-encoding |
| Offline outbox | · | ✓ | flushed when the library opens or on **Upload now** |
| Presigned upload and idempotent `/media/complete` | ✓ | ✓ | |
| Multipart upload in 64 MB parts | ✓ | · | for clients with large files; the app uses one PUT |
| True diarisation, multilingual | ✓ | ✓ | Deepgram nova-3 `language=multi` when a Deepgram key is set; Whisper otherwise |
| One-call analysis: title, dialogue, English copy, summary, extractions | ✓ | ✓ | `gpt-4o-mini` |
| Detailed, bullet and minutes summaries, chat, translate, rewrite, tone, custom prompt | ✓ | ✓ | |
| Library: search, sort, folders, tags, favourites, pins, archive | ✓ | ✓ | folders can nest in the API; the app makes top-level folders |
| Transcript editing, speaker rename, manual transcript | ✓ | ✓ | the manual transcript route has no button; edits and renames do |
| Export TXT, Markdown, Word, PDF | ✓ | ✓ | |
| Integrations: Slack, Teams, Notion, Trello, Jira, webhook | ✓ | ✓ | each needs its URL or token, entered on the Connections screen or with `PUT /integrations` |
| Google Calendar and Tasks | ✓ | ✓ | the phone passes a Google access token for the scopes it needs |
| Gmail draft | ✓ | · | the route exists; the Connections screen shows Gmail as linked to the Google account only |
| Collaboration: workspaces, members, recording shares | ✓ | · | |
| Meeting templates: create, list, delete, apply to a transcript | ✓ | · | |
| Automation rules | ✓ | · | run after every analysis; push to Slack, Teams, Notion, Trello, Jira or a webhook; Google actions are skipped because they need the phone's token |
| Ask across recordings and search | ✓ | ✓ | the Ask screen; semantic search needs `LITE_MODE=false`, keyword search otherwise |
| Knowledge graph of entities and topics | ✓ | · | |
| Digest | ✓ | ✓ | the Analytics screen |
| Coaching, conversation analytics, productivity numbers | ✓ | · | |
| Voice-command intent | ✓ | · | |
| Live transcription over `/api/live/transcribe` | ✓ | · | refused while `LITE_MODE` is on |
| Push, FCM HTTP v1 | ✓ | ✓ | a service-account send on **Transcript ready**, the device-token route, and the Firebase Messaging client with `google-services.json` |
| Over-the-air updates | ✓ | ✓ | the master admin publishes the installed APK; other phones offer it |
| Provider keys at runtime | ✓ | · | Deepgram and OpenAI keys in `ta_app_settings` win over the environment |
| Lite mode | ✓ | · | `LITE_MODE`, on by default |
| HTTPS and IPv6 | ✓ | ✓ | CloudFront in front of the API |

## Needs your action

- **FCM push, server side.** The deployed `BACKEND_ENV` must set
  `FCM_SERVICE_ACCOUNT_FILE=fcm-service-account.json`. The deploy already writes that file from the
  `FCM_SERVICE_ACCOUNT_JSON` secret; without the variable the backend stores device tokens and sends
  nothing. The client side is done: `google-services.json` and the `com.google.gms.google-services`
  plugin have been in `android/app` since 28 June 2026. A desktop ("installed") OAuth
  `client_secret_*.json` is not `google-services.json` and is not used by the app.
- **Google sign-in on Android.** Register an Android-type OAuth client for the package
  `com.transcriptai.app` with the build's SHA-1, in the same Google project as the web client.
- **Microsoft and Yahoo sign-in.** Register each app, put the client IDs in `android/local.properties`
  (`microsoftClientId`, `yahooClientId`) and the IDs and secrets in the repository secrets. Microsoft
  returns to `com.transcriptai.app://oauth`; Yahoo needs HTTPS, so it returns to
  `<api base>/auth/yahoo/callback`, which bounces to that scheme.
- **Each third-party integration.** Enter its webhook URL or API token on the Connections screen, or
  with `PUT /integrations`.
- **AI credit.** Summaries, actions, the clean dialogue and chat need an OpenAI account with quota.
  `GET /health/ai` says whether the configured key works; while it does not, analysis jobs wait and
  retry every 15 minutes, so nothing has to be transcribed again.
- **Heavy features on the box.** Set `LITE_MODE=false` in `BACKEND_ENV` only on a larger instance; the
  default `t3.micro` should stay lite.

## Still not built

- An Android screen for live transcription; the WebSocket exists.
- An Android client for voice commands; the intent endpoint exists, with no always-listening client.
- Android screens for collaboration, meeting templates, automation rules and the Gmail draft; their
  routes exist.
- A real-time meeting assistant, a knowledge-graph view, scheduled coaching, and a builder for custom
  multi-step AI workflows.
- Meeting-bot ingestion from Zoom, Google Meet or Teams. Only outbound pushes are built; the Google Meet
  row on the Connections screen reflects the Google link only.

## Seams

So the pieces above slot in cleanly:

- `ProcessingJob.jobType` is a free-form string, so a new job kind needs no migration.
- Every AI helper is a small async function in `backend/app/services/ai.py`.
- Integration connectors dispatch through `backend/app/services/integrations.py`, and automation actions
  map onto them in `backend/app/services/automation.py`.
- Provider keys are resolved per use by `resolve_settings()` in `backend/app/services/app_settings.py`,
  so a new provider's key can be overridden at runtime the same way.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="readme-kit/assets/footer-dark.svg">
  <img src="readme-kit/assets/footer-light.svg" width="100%" alt="Every voice in the room, kept and put to work. Colloquia: Kotlin and Compose, FastAPI, Deepgram, OpenAI.">
</picture>
