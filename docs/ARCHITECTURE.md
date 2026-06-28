# Colloquia — Architecture

Colloquia is an API-first audio-intelligence app: an Android client and a FastAPI backend over JWT.
Heavy work (transcription, AI analysis) is decoupled from upload via a durable job queue drained by a
single elected worker, so a free-tier box stays responsive.

## 1. System context

```mermaid
flowchart TB
  subgraph Client
    A["Android (Compose)<br/>com.transcriptai.app"]
  end
  subgraph Edge
    CF["CloudFront<br/>HTTPS + IPv6<br/>d2nrls693zgomu.cloudfront.net"]
  end
  subgraph "AWS EC2 (t3.micro, free-tier)"
    NG["nginx :80"] --> UV["FastAPI / uvicorn :8010<br/>systemd: colloquia"]
    UV --> WK["queue worker<br/>(elected, in-process)"]
  end
  subgraph Managed
    DB[("Supabase Postgres<br/>ta_* tables")]
    S3[("AWS S3<br/>fieldrepo-media-* bucket")]
    OAI["OpenAI<br/>Whisper + gpt-4o-mini + embeddings"]
    DG["Deepgram<br/>diarized STT"]
    FB["Firebase FCM v1"]
    G["Google Identity / Calendar / Tasks / Gmail"]
  end

  A -->|JWT REST| CF --> NG
  A -->|presigned PUT/GET| S3
  A -->|Google ID token| G
  UV <--> DB
  UV --> S3
  WK --> OAI
  WK --> DG
  WK --> FB --> A
  UV --> G
```

## 2. Upload → transcription → analysis flow

```mermaid
sequenceDiagram
  participant App
  participant API as FastAPI
  participant S3
  participant Q as ProcessingJob queue
  participant W as Worker
  participant STT as Whisper/Deepgram
  participant AI as gpt-4o-mini

  App->>API: POST /media/presign
  API-->>App: signed PUT url + objectKey
  App->>S3: PUT audio bytes
  App->>API: POST /media/complete (objectKey)
  Note over API: idempotent on objectKey
  API->>API: create Recording
  API->>Q: enqueue TRANSCRIPTION
  API-->>App: Recording (transcriptStatus=QUEUED)
  W->>Q: claim job (priority, runAfter)
  W->>S3: get object bytes
  W->>STT: transcribe (segments + speakers)
  W->>API: store Transcript + segments
  W->>App: FCM "Transcript ready"
  W->>Q: enqueue ANALYSIS
  W->>AI: title, summaries, action items, entities
  W->>API: store Summary/ActionItem/ExtractedItem
  Note over W: also indexes embeddings + runs automation rules (unless lite mode)
```

### Resilience
- **Idempotent `/complete`** keyed on the unique `objectKey` → safe client retries (504-proof).
- **Rate limits (HTTP 429/503)** → global cooldown + requeue **without** consuming an attempt.
- **Off-peak / idle gating** for heavy work; **single elected worker** (advisory file lock) so only one
  process drains the queue.
- **Lite mode** skips embedding indexing + live STT on small hardware.

## 3. Data model (namespaced `ta_*`, shares the DB with the field-repo)

```mermaid
erDiagram
  User ||--o{ Recording : owns
  User ||--o{ Folder : owns
  User ||--o{ Tag : owns
  Folder ||--o{ Recording : contains
  Recording ||--o| Transcript : has
  Transcript ||--o{ TranscriptSegment : has
  Recording ||--o{ Summary : has
  Recording ||--o{ ActionItem : has
  Recording ||--o{ ExtractedItem : has
  Recording ||--o{ ChatMessage : has
  Recording ||--o{ RecordingTag : tagged
  Tag ||--o{ RecordingTag : tags
  Recording ||--o{ ProcessingJob : queued
  User ||--o{ Integration : connects
  User ||--o{ Workspace : owns
  Workspace ||--o{ WorkspaceMember : has
  User ||--o{ MeetingTemplate : owns
  User ||--o{ AutomationRule : owns
  User ||--o{ Embedding : owns
  User ||--o{ DeviceToken : registers
  User ||--o{ Digest : receives
```

Status/role/kind columns are `String` (not Prisma enums) to avoid Postgres TYPE collisions with the
co-resident app. New tables are applied additively via `prisma/*.sql` (`db execute`), never `db push`
(which fails here with P1014) and never `migrate` (which would touch the shared `_prisma_migrations`).

## 4. AI capability map

```mermaid
flowchart LR
  T["Transcript text"] --> TT["title"]
  T --> SUM["summaries<br/>brief/detailed/bullets/minutes"]
  T --> EX["extract<br/>actions/decisions/takeaways/deadlines<br/>entities/keywords/topics"]
  T --> CH["chat over transcript"]
  T --> TR["translate / rewrite / tone / custom"]
  T --> EMB["embeddings"] --> SS["semantic + cross-transcript search<br/>knowledge graph"]
  T --> INS["digest / coaching / conversation analytics"]
  EX --> PROD["Google Calendar / Tasks / Gmail"]
  EX --> INTEG["Slack / Notion / Trello / Jira / webhook"]
  EX --> AUTO["automation rules (post-analysis)"]
```

## 5. Deployment

```mermaid
flowchart LR
  dev["git push main"] --> gh["GitHub Actions"]
  gh --> ci["CI: backend import + Android APK"]
  gh --> dep["deploy-backend.yml"]
  dep -->|rsync + ssh| ec2["EC2 box"]
  dep -->|writes| env[".env + fcm-service-account.json (from secrets)"]
  dep -->|db execute| tables["ta_* tables"]
  dep -->|systemctl restart| svc["colloquia.service"]
  tf["Terraform"] -->|provisions| ec2
  tf -->|provisions| cfd["CloudFront"]
```

Secrets (GitHub Actions): `EC2_HOST`, `EC2_SSH_KEY`, `BACKEND_ENV`, `FCM_SERVICE_ACCOUNT_JSON`.
The EC2 box is stateless (DB on Supabase, media on S3) — it can be rebuilt from Terraform anytime.
