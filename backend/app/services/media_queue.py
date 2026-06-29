"""Durable processing queue, drained by a single elected worker (see main.py).

Ported from the field-repository backend and adapted to the TranscriptAI data model. A TRANSCRIPTION
job produces the Transcript + timestamped, speaker-labelled segments and then CHAINS an ANALYSIS job
(AI title, summaries, action items, extractions) so the transcript shows up fast and the AI
enrichments fill in afterwards. Throttling (HTTP 429/503) is treated as transient: a global cooldown
plus requeue-without-consuming-an-attempt, so every clip is transcribed eventually.
"""

import asyncio
import os
from datetime import UTC, datetime, timedelta
from typing import Any

from fastapi.encoders import jsonable_encoder
from prisma import Json

from app.core.config import Settings, get_settings
from app.core.db import db
from app.services.ai import (
    analyze_full,
    transcribe_audio_bytes,
)
from app.services.app_settings import (
    load_app_settings,
    transcription_mode,
    within_processing_window,
)
from app.services.s3 import get_object_bytes

TRANSCRIPTION = "TRANSCRIPTION"
ANALYSIS = "ANALYSIS"
QUEUED = "QUEUED"
PROCESSING = "PROCESSING"
COMPLETED = "COMPLETED"
FAILED = "FAILED"
UNAVAILABLE = "UNAVAILABLE"
RATE_LIMITED = "RATE_LIMITED"
EMPTY = "EMPTY"

STALE_PROCESSING_AFTER = timedelta(minutes=30)
RATE_LIMIT_BASE_SECONDS = 30
RATE_LIMIT_MAX_SECONDS = 900
IDLE_LOAD_FACTOR = 0.6
# A whole recording's analysis (conversation, title, summary, extractions) is now ONE chat call
# (ai.analyze_full), so there is no per-call fan-out to pace or make resumable — a 429 simply requeues
# the single-call job. The other summary types (DETAILED / BULLETS / MINUTES) stay on-demand.

# Single elected worker, so module-level cooldown state is safe.
_rate_limit_cooldown_until: datetime | None = None
_consecutive_rate_limits = 0


class RateLimited(Exception):
    def __init__(self, retry_after: float | None = None) -> None:
        super().__init__("Transcription rate-limited")
        self.retry_after = retry_after


def _server_is_idle() -> bool:
    try:
        load1 = os.getloadavg()[0]
    except (OSError, AttributeError):
        return False
    return load1 < (os.cpu_count() or 1) * IDLE_LOAD_FACTOR


def _transcription_in_cooldown(now: datetime | None = None) -> bool:
    return _rate_limit_cooldown_until is not None and (now or datetime.now(UTC)) < _rate_limit_cooldown_until


def _enter_rate_limit_cooldown(retry_after: float | None) -> None:
    global _rate_limit_cooldown_until, _consecutive_rate_limits
    _consecutive_rate_limits += 1
    if retry_after and retry_after > 0:
        delay = max(retry_after, RATE_LIMIT_BASE_SECONDS)
    else:
        delay = min(RATE_LIMIT_BASE_SECONDS * (2 ** (_consecutive_rate_limits - 1)), RATE_LIMIT_MAX_SECONDS)
    _rate_limit_cooldown_until = datetime.now(UTC) + timedelta(seconds=delay)


def _clear_rate_limit_cooldown() -> None:
    global _rate_limit_cooldown_until, _consecutive_rate_limits
    _rate_limit_cooldown_until = None
    _consecutive_rate_limits = 0


# --------------------------------------------------------------------------- enqueue

async def enqueue_job(
    recording_id: str,
    job_type: str,
    requested_by_id: str | None,
    settings: Settings | None = None,
) -> Any:
    settings = settings or get_settings()
    job = await db.processingjob.create(
        data={
            "jobType": job_type,
            "status": QUEUED,
            "priority": 50 if job_type == TRANSCRIPTION else 70,
            "maxAttempts": max(settings.media_queue_job_max_attempts, 1),
            "recordingId": recording_id,
            "requestedById": requested_by_id,
        }
    )
    if job_type == TRANSCRIPTION:
        await db.recording.update(
            where={"id": recording_id},
            data={"transcriptStatus": QUEUED, "status": PROCESSING},
        )
    return job


# --------------------------------------------------------------------------- drain

async def process_next_jobs(
    limit: int | None = None,
    worker_id: str = "api-worker",
    settings: Settings | None = None,
) -> dict[str, int]:
    settings = settings or get_settings()
    batch_size = max(1, limit or settings.media_queue_batch_size)
    await recover_stale_processing_jobs()
    now = datetime.now(UTC)
    where: dict[str, Any] = {"status": QUEUED, "runAfter": {"lte": now}}
    app_settings = await load_app_settings()
    allow_transcription = (
        (within_processing_window(app_settings) or _server_is_idle())
        and not _transcription_in_cooldown(now)
    )
    if not allow_transcription:
        where["jobType"] = {"not": TRANSCRIPTION}
    jobs = await db.processingjob.find_many(
        where=where,
        include={"recording": True},
        order=[{"priority": "asc"}, {"createdAt": "asc"}],
        take=batch_size,
    )

    processed = succeeded = failed = 0
    for job in jobs:
        processed += 1
        try:
            locked = await _lock_job(job.id, worker_id)
            if locked is None:
                continue
            await _process_job(locked, settings)
            succeeded += 1
            if locked.jobType == TRANSCRIPTION:
                _clear_rate_limit_cooldown()
        except RateLimited as exc:
            await _defer_rate_limited_job(job, exc.retry_after)
            _enter_rate_limit_cooldown(exc.retry_after)
            break
        except Exception as exc:  # noqa: BLE001
            failed += 1
            await _handle_job_failure(job.id, exc)
    return {"processed": processed, "succeeded": succeeded, "failed": failed}


async def _defer_rate_limited_job(job: Any, retry_after: float | None) -> None:
    delay = retry_after if (retry_after and retry_after > 0) else RATE_LIMIT_BASE_SECONDS
    await db.processingjob.update(
        where={"id": job.id},
        data={
            "status": QUEUED,
            "lockedAt": None,
            "lockedBy": None,
            "attempts": job.attempts,
            "runAfter": datetime.now(UTC) + timedelta(seconds=delay),
            "error": "Rate-limited; awaiting cooldown before automatic retry.",
        },
    )


async def recover_stale_processing_jobs() -> int:
    cutoff = datetime.now(UTC) - STALE_PROCESSING_AFTER
    stale = await db.processingjob.find_many(
        where={"status": PROCESSING, "lockedAt": {"lt": cutoff}}, take=25
    )
    for job in stale:
        await db.processingjob.update(
            where={"id": job.id},
            data={
                "status": QUEUED,
                "lockedAt": None,
                "lockedBy": None,
                "runAfter": datetime.now(UTC),
                "error": "Recovered after worker interruption.",
            },
        )
    return len(stale)


async def _lock_job(job_id: str, worker_id: str) -> Any | None:
    job = await db.processingjob.find_unique(where={"id": job_id})
    if not job or job.status != QUEUED:
        return None
    now = datetime.now(UTC)
    return await db.processingjob.update(
        where={"id": job_id},
        data={
            "status": PROCESSING,
            "lockedAt": now,
            "lockedBy": worker_id,
            "startedAt": now,
            "attempts": job.attempts + 1,
            "error": None,
        },
        include={"recording": True},
    )


async def _process_job(job: Any, settings: Settings) -> None:
    if job.jobType == TRANSCRIPTION:
        await _run_transcription(job, settings)
        return
    if job.jobType == ANALYSIS:
        await _run_analysis(job, settings)
        return
    raise RuntimeError(f"Unsupported job type: {job.jobType}")


# --------------------------------------------------------------------------- transcription job

async def _run_transcription(job: Any, settings: Settings) -> None:
    recording = job.recording
    await db.recording.update(
        where={"id": recording.id},
        data={"transcriptStatus": PROCESSING, "status": PROCESSING},
    )
    content = await asyncio.to_thread(get_object_bytes, recording.objectKey)
    result = await transcribe_audio_bytes(
        content,
        recording.originalFilename or "recording.m4a",
        recording.mimeType or "audio/m4a",
        settings,
    )
    status = str(result.get("status") or FAILED).upper()

    if status == RATE_LIMITED:
        await db.recording.update(
            where={"id": recording.id},
            data={"transcriptStatus": QUEUED, "transcriptError": result.get("message")},
        )
        raise RateLimited(result.get("retryAfter"))

    if status == UNAVAILABLE:
        await db.recording.update(
            where={"id": recording.id},
            data={"transcriptStatus": UNAVAILABLE, "transcriptError": result.get("message")},
        )
        await _finalize_unavailable_job(job.id, result, result.get("message"))
        return

    if status not in {COMPLETED, EMPTY}:
        await db.recording.update(
            where={"id": recording.id},
            data={"transcriptStatus": FAILED, "status": FAILED, "transcriptError": result.get("message")},
        )
        raise RuntimeError(result.get("message") or "Transcription failed")

    await _store_transcript(recording, result, settings)
    await _complete_job(job.id, {"status": status, "segments": len(result.get("segments") or [])})
    # Push "transcript ready" to the owner's devices (best-effort; no-op without FCM configured).
    try:
        from app.services import fcm
        await fcm.send_to_user(recording.userId, "Transcript ready", recording.title or "Your recording", settings)
    except Exception:  # noqa: BLE001
        pass
    # Chain the AI analysis pass so the transcript appears immediately and enrichments fill in after.
    if status == COMPLETED:
        await enqueue_job(recording.id, ANALYSIS, job.requestedById, settings)


async def _store_transcript(recording: Any, result: dict[str, Any], settings: Settings) -> None:
    raw_text = result.get("text") or ""
    segments = result.get("segments") or []

    # No AI calls here: the raw transcript + segments are stored immediately so the transcript shows up
    # fast. The refined/translated CONVERSATION, title, summary and action items are all produced by a
    # SINGLE downstream analyze_full() call in the chained ANALYSIS job (see _run_analysis). Segment
    # speaker labels are only set when the provider (Deepgram) already returned true diarization.
    speakers = (
        [s.get("speaker") for s in segments]
        if segments and any(s.get("speaker") for s in segments)
        else [None] * len(segments)
    )

    duration_ms = int(float(result.get("duration") or 0) * 1000) or (segments[-1]["endMs"] if segments else None)
    transcript = await db.transcript.upsert(
        where={"recordingId": recording.id},
        data={
            "create": {
                "recordingId": recording.id,
                "status": COMPLETED,
                "rawText": raw_text,
                "language": result.get("language"),
                "model": settings.openai_transcription_model,
                "durationMs": duration_ms,
            },
            "update": {
                "status": COMPLETED,
                "rawText": raw_text,
                # Leave refined/translated to the analysis pass; clear stale values on a re-transcribe.
                "refinedText": None,
                "translatedText": None,
                "language": result.get("language"),
                "model": settings.openai_transcription_model,
                "durationMs": duration_ms,
            },
        },
    )
    # Replace segments wholesale (idempotent re-runs).
    await db.transcriptsegment.delete_many(where={"transcriptId": transcript.id})
    if segments:
        await db.transcriptsegment.create_many(
            data=[
                {
                    "transcriptId": transcript.id,
                    "idx": i,
                    "speaker": speakers[i] if i < len(speakers) else None,
                    "startMs": seg["startMs"],
                    "endMs": seg["endMs"],
                    "text": seg["text"],
                }
                for i, seg in enumerate(segments)
            ]
        )
    update: dict[str, Any] = {
        "transcriptStatus": COMPLETED,
        "status": COMPLETED,
        "transcriptError": None,
        "language": result.get("language"),
    }
    if duration_ms:
        update["durationSec"] = int(duration_ms / 1000)
    await db.recording.update(where={"id": recording.id}, data=update)


# --------------------------------------------------------------------------- analysis job

async def _run_analysis(job: Any, settings: Settings) -> None:
    recording = job.recording
    transcript = await db.transcript.find_unique(where={"recordingId": recording.id})
    raw_text = (transcript.rawText or "") if transcript else ""
    if not raw_text.strip():
        await _complete_job(job.id, {"status": EMPTY})
        return

    # ONE call does everything: refined (+ optional English) conversation, title, summary, and all
    # extractions. A 429 here just requeues the whole (single-call) job — no fan-out to re-throttle.
    mode = transcription_mode(await load_app_settings())
    res = await analyze_full(raw_text, mode == "REFINED_TRANSLATED", settings)
    status = str(res.get("status") or "").upper()
    if status == RATE_LIMITED:
        raise RateLimited(res.get("retryAfter"))
    if status != COMPLETED or not isinstance(res.get("data"), dict):
        await _complete_job(job.id, {"status": status or FAILED, "message": res.get("message")})
        return
    data = res["data"]

    # Conversation (refined + optional English translation) and detected language.
    refined = (str(data.get("refinedConversation") or "").strip()) or None
    english = (str(data.get("englishConversation") or "").strip()) or None
    language = data.get("language") or (transcript.language if transcript else None)
    await db.transcript.update(
        where={"recordingId": recording.id},
        data={"refinedText": refined, "translatedText": english, "language": language},
    )

    # Title (adopt as the display title when the user kept the default) + language on the recording.
    rec_update: dict[str, Any] = {}
    title = str(data.get("title") or "").strip().strip('"')
    if title:
        rec_update["aiTitle"] = title
        if not recording.title or recording.title.lower().startswith(("recording", "new recording")):
            rec_update["title"] = title
    if language:
        rec_update["language"] = language
    if rec_update:
        await db.recording.update(where={"id": recording.id}, data=rec_update)

    # Brief summary (the only one generated automatically; others are on-demand).
    summary = str(data.get("summary") or "").strip()
    if summary:
        await db.summary.upsert(
            where={"recordingId_type": {"recordingId": recording.id, "type": "BRIEF"}},
            data={
                "create": {"recordingId": recording.id, "type": "BRIEF", "content": summary, "model": res.get("model")},
                "update": {"content": summary, "model": res.get("model")},
            },
        )

    # Action items / decisions / takeaways / deadlines + entities / keywords / topics.
    await _store_extractions(recording.id, data)

    # Phase 2 post-analysis hooks (best-effort; never fail the job):
    # 1) index segments for semantic / cross-transcript search (skipped in lite mode),
    # 2) fire ON_ANALYZED automation rules (push to Slack/Notion/etc.).
    try:
        from app.services import automation, embeddings
        await embeddings.index_recording(recording.id, recording.userId, settings)
        await automation.run_rules_for_recording(recording.id, "ON_ANALYZED")
    except Exception:  # noqa: BLE001
        pass

    await _complete_job(job.id, {"status": COMPLETED})


async def _store_extractions(recording_id: str, data: dict[str, Any]) -> None:
    # Wipe prior extractions so a re-run doesn't duplicate.
    await db.actionitem.delete_many(where={"recordingId": recording_id})
    await db.extracteditem.delete_many(where={"recordingId": recording_id})

    def _parse_date(value: Any) -> datetime | None:
        if not value or not isinstance(value, str):
            return None
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None

    action_rows: list[dict[str, Any]] = []
    for item in data.get("actionItems") or []:
        if isinstance(item, dict) and item.get("text"):
            action_rows.append({
                "recordingId": recording_id, "kind": "ACTION", "text": str(item["text"]),
                "assignee": item.get("assignee"), "dueDate": _parse_date(item.get("dueDate")),
            })
        elif isinstance(item, str) and item.strip():
            action_rows.append({"recordingId": recording_id, "kind": "ACTION", "text": item.strip()})
    for d in data.get("decisions") or []:
        if str(d).strip():
            action_rows.append({"recordingId": recording_id, "kind": "DECISION", "text": str(d).strip()})
    for t in data.get("takeaways") or []:
        if str(t).strip():
            action_rows.append({"recordingId": recording_id, "kind": "TAKEAWAY", "text": str(t).strip()})
    for dl in data.get("deadlines") or []:
        if isinstance(dl, dict) and dl.get("text"):
            action_rows.append({
                "recordingId": recording_id, "kind": "DEADLINE", "text": str(dl["text"]),
                "dueDate": _parse_date(dl.get("dueDate")),
            })
        elif isinstance(dl, str) and dl.strip():
            action_rows.append({"recordingId": recording_id, "kind": "DEADLINE", "text": dl.strip()})
    if action_rows:
        await db.actionitem.create_many(data=action_rows)

    extracted_rows: list[dict[str, Any]] = []
    for kind, key in (("ENTITY", "entities"), ("KEYWORD", "keywords"), ("TOPIC", "topics")):
        for v in data.get(key) or []:
            if str(v).strip():
                extracted_rows.append({"recordingId": recording_id, "kind": kind, "value": str(v).strip()})
    if extracted_rows:
        await db.extracteditem.create_many(data=extracted_rows)


# --------------------------------------------------------------------------- admin: transcribe now

async def transcribe_recording_now(recording: Any, settings: Settings | None = None) -> dict[str, Any]:
    """Force a transcription immediately (bypassing the queue + off-peak window), then run analysis.
    Mirrors the worker path so manual and queued runs agree. Never raises on AI failure."""
    settings = settings or get_settings()
    content = await asyncio.to_thread(get_object_bytes, recording.objectKey)
    await db.recording.update(
        where={"id": recording.id}, data={"transcriptStatus": PROCESSING, "status": PROCESSING}
    )
    result = await transcribe_audio_bytes(
        content, recording.originalFilename or "recording.m4a", recording.mimeType or "audio/m4a", settings
    )
    status = str(result.get("status") or FAILED).upper()
    if status == RATE_LIMITED:
        await db.recording.update(
            where={"id": recording.id},
            data={"transcriptStatus": QUEUED, "transcriptError": result.get("message")},
        )
        await enqueue_job(recording.id, TRANSCRIPTION, None, settings)
        return result
    if status not in {COMPLETED, EMPTY}:
        await db.recording.update(
            where={"id": recording.id},
            data={"transcriptStatus": status, "transcriptError": result.get("message")},
        )
        return result
    await _store_transcript(recording, result, settings)
    if status == COMPLETED:
        await enqueue_job(recording.id, ANALYSIS, None, settings)
    return result


# --------------------------------------------------------------------------- job finalizers

async def _complete_job(job_id: str, result: dict[str, Any]) -> None:
    await db.processingjob.update(
        where={"id": job_id},
        data={
            "status": COMPLETED,
            "lockedAt": None,
            "lockedBy": None,
            "completedAt": datetime.now(UTC),
            "result": Json(jsonable_encoder(result)),
            "error": None,
        },
    )


async def _finalize_unavailable_job(job_id: str, result: dict[str, Any], message: Any) -> None:
    await db.processingjob.update(
        where={"id": job_id},
        data={
            "status": FAILED,
            "lockedAt": None,
            "lockedBy": None,
            "completedAt": datetime.now(UTC),
            "result": Json(jsonable_encoder(result)),
            "error": str(message or "Required AI API key is not configured."),
        },
    )


async def _handle_job_failure(job_id: str, exc: Exception) -> None:
    job = await db.processingjob.find_unique(where={"id": job_id})
    if not job:
        return
    now = datetime.now(UTC)
    error = str(exc)[:2000]
    exhausted = job.attempts >= job.maxAttempts
    retry_delay = timedelta(minutes=min(60, 2 ** max(job.attempts - 1, 0)))
    await db.processingjob.update(
        where={"id": job_id},
        data={
            "status": FAILED if exhausted else QUEUED,
            "lockedAt": None,
            "lockedBy": None,
            "runAfter": now if exhausted else now + retry_delay,
            "completedAt": now if exhausted else None,
            "error": error,
        },
    )
    if exhausted and job.jobType == TRANSCRIPTION:
        await db.recording.update(
            where={"id": job.recordingId},
            data={"transcriptStatus": FAILED, "status": FAILED, "transcriptError": error},
        )
