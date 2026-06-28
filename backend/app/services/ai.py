"""OpenAI-backed transcription + analysis.

Ported and extended from the field-repository backend. Transcription now requests
``response_format=verbose_json`` with segment timestamps so the UI can show timestamped, navigable
segments (and so we have anchors for speaker labelling). gpt-4o-mini powers refinement, AI titles,
summaries, extraction, chat, translation, rewrite and tone conversion.
"""

import asyncio
import json
import logging
from typing import Any

import requests

from app.core.config import Settings

logger = logging.getLogger(__name__)

# Whisper rejects files at/over 25 MB. Stay under it and split anything larger into ~10-minute mono
# segments transcribed sequentially, stitched with their timestamps offset by the chunk start.
WHISPER_MAX_BYTES = 24 * 1024 * 1024
TRANSCRIPTION_CHUNK_MS = 10 * 60 * 1000

_REFINE_MAX_CHARS = 48_000
# Cap transcript text fed to analysis/chat so a runaway transcript can't blow up the token bill.
_ANALYSIS_MAX_CHARS = 48_000


# --------------------------------------------------------------------------- transcription

def _post_openai_transcription(
    content: bytes, filename: str, mime_type: str, settings: Settings
) -> dict[str, Any]:
    """One Whisper call returning text + timestamped segments (verbose_json)."""
    response = requests.post(
        "https://api.openai.com/v1/audio/transcriptions",
        headers={"Authorization": f"Bearer {settings.openai_api_key}"},
        data={
            "model": settings.openai_transcription_model,
            "response_format": "verbose_json",
            "timestamp_granularities[]": "segment",
        },
        files={"file": (filename, content, mime_type or "application/octet-stream")},
        timeout=300,
    )
    response.raise_for_status()
    payload = response.json()
    text = str(payload.get("text") or "").strip()
    segments = _segments_from_payload(payload, offset_ms=0)
    return {
        "available": True,
        "status": "COMPLETED" if text else "EMPTY",
        "text": text,
        "segments": segments,
        "language": payload.get("language"),
        "duration": payload.get("duration"),
    }


def _segments_from_payload(payload: dict[str, Any], offset_ms: int) -> list[dict[str, Any]]:
    raw = payload.get("segments") or []
    out: list[dict[str, Any]] = []
    for seg in raw:
        seg_text = str(seg.get("text") or "").strip()
        if not seg_text:
            continue
        start_ms = int(float(seg.get("start") or 0) * 1000) + offset_ms
        end_ms = int(float(seg.get("end") or 0) * 1000) + offset_ms
        out.append({"startMs": start_ms, "endMs": max(end_ms, start_ms), "text": seg_text})
    return out


def _split_audio_into_chunks(content: bytes) -> list[tuple[bytes, str, str, int]] | None:
    """Split audio into <=10-minute mono MP3 chunks under the Whisper size limit. Each tuple carries
    the chunk's start offset (ms) so segment timestamps can be made absolute. Returns None when
    pydub/ffmpeg is unavailable or the bytes can't be decoded (caller falls back to single-shot)."""
    try:
        import io

        from pydub import AudioSegment
    except Exception:  # noqa: BLE001
        logger.warning("pydub/ffmpeg unavailable; long audio cannot be chunked for transcription")
        return None
    try:
        audio = AudioSegment.from_file(io.BytesIO(content))
    except Exception as exc:  # noqa: BLE001
        logger.warning("Unable to decode audio for chunked transcription: %s", exc)
        return None

    chunks: list[tuple[bytes, str, str, int]] = []
    for index, start in enumerate(range(0, max(len(audio), 1), TRANSCRIPTION_CHUNK_MS)):
        segment = audio[start : start + TRANSCRIPTION_CHUNK_MS].set_channels(1)
        buffer = io.BytesIO()
        segment.export(buffer, format="mp3", bitrate="64k")
        chunks.append((buffer.getvalue(), f"chunk-{index + 1:03d}.mp3", "audio/mpeg", start))
    return chunks or None


def _transcribe_sync(
    content: bytes, filename: str, mime_type: str, settings: Settings
) -> dict[str, Any]:
    if len(content) <= WHISPER_MAX_BYTES:
        return _post_openai_transcription(content, filename, mime_type, settings)

    chunks = _split_audio_into_chunks(content)
    if not chunks:
        return _post_openai_transcription(content, filename, mime_type, settings)

    pieces: list[str] = []
    all_segments: list[dict[str, Any]] = []
    for chunk_bytes, chunk_name, chunk_mime, offset_ms in chunks:
        result = _post_openai_transcription(chunk_bytes, chunk_name, chunk_mime, settings)
        # Re-offset each chunk's segments to absolute time (the chunk was transcribed from 0).
        for seg in result.get("segments") or []:
            seg = dict(seg)
            seg["startMs"] += offset_ms
            seg["endMs"] += offset_ms
            all_segments.append(seg)
        piece = str(result.get("text") or "").strip()
        if piece:
            pieces.append(piece)
    text = " ".join(pieces).strip()
    return {
        "available": True,
        "status": "COMPLETED" if text else "EMPTY",
        "text": text,
        "segments": all_segments,
        "chunks": len(chunks),
    }


def _post_deepgram(content: bytes, mime_type: str, settings: Settings) -> dict[str, Any]:
    """Deepgram prerecorded transcription WITH true acoustic diarization (diarize=true). Returns the
    same shape as the Whisper path but each segment carries a real `speaker` label."""
    response = requests.post(
        "https://api.deepgram.com/v1/listen",
        params={"diarize": "true", "punctuate": "true", "utterances": "true", "smart_format": "true"},
        headers={"Authorization": f"Token {settings.deepgram_api_key}", "Content-Type": mime_type or "audio/m4a"},
        data=content,
        timeout=300,
    )
    response.raise_for_status()
    payload = response.json()
    results = payload.get("results", {})
    utterances = results.get("utterances") or []
    segments: list[dict[str, Any]] = []
    parts: list[str] = []
    for u in utterances:
        text = str(u.get("transcript") or "").strip()
        if not text:
            continue
        parts.append(text)
        segments.append({
            "startMs": int(float(u.get("start") or 0) * 1000),
            "endMs": int(float(u.get("end") or 0) * 1000),
            "text": text,
            "speaker": f"Speaker {int(u.get('speaker', 0)) + 1}",
        })
    text = " ".join(parts).strip()
    if not text:
        # Fall back to the channel transcript when utterances are empty.
        alt = (results.get("channels", [{}])[0].get("alternatives", [{}])[0])
        text = str(alt.get("transcript") or "").strip()
    return {
        "available": True,
        "status": "COMPLETED" if text else "EMPTY",
        "text": text,
        "segments": segments,
        "provider": "deepgram",
    }


async def transcribe_audio_bytes(
    content: bytes, filename: str, mime_type: str, settings: Settings
) -> dict[str, Any]:
    # Prefer Deepgram when a key is configured: it gives TRUE speaker diarization in one call. Fall back
    # to Whisper (+ chat-model speaker labelling) on any Deepgram error, or when no Deepgram key is set.
    if settings.deepgram_api_key:
        try:
            return await asyncio.to_thread(_post_deepgram, content, mime_type, settings)
        except requests.HTTPError as exc:
            code = exc.response.status_code if exc.response is not None else None
            if code in {429, 503}:
                return {"available": True, "status": "RATE_LIMITED", "text": None, "segments": [],
                        "retryAfter": None, "message": f"Deepgram rate-limited (HTTP {code})."}
            logger.info("Deepgram failed (HTTP %s); falling back to Whisper", code)
        except requests.RequestException as exc:
            logger.info("Deepgram error (%s); falling back to Whisper", exc)

    if not settings.openai_api_key:
        return {
            "available": False,
            "status": "UNAVAILABLE",
            "text": None,
            "segments": [],
            "message": "Transcription unavailable: no DEEPGRAM_API_KEY or OPENAI_API_KEY configured.",
        }
    try:
        return await asyncio.to_thread(_transcribe_sync, content, filename, mime_type, settings)
    except requests.HTTPError as exc:
        # 429 / 503 are transient throttling, not real failures -> RATE_LIMITED so the queue backs off
        # and retries WITHOUT consuming attempts. Honour Retry-After when present.
        response = exc.response
        code = response.status_code if response is not None else None
        if code in {429, 503}:
            retry_after = None
            if response is not None and response.headers.get("Retry-After"):
                try:
                    retry_after = float(response.headers["Retry-After"])
                except (TypeError, ValueError):
                    retry_after = None
            return {
                "available": True,
                "status": "RATE_LIMITED",
                "text": None,
                "segments": [],
                "retryAfter": retry_after,
                "message": f"Transcription rate-limited (HTTP {code}); will retry automatically.",
            }
        return {"available": True, "status": "FAILED", "text": None, "segments": [], "message": str(exc)}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "text": None, "segments": [], "message": str(exc)}


# --------------------------------------------------------------------------- chat helpers

def _post_openai_chat(
    messages: list[dict[str, str]], settings: Settings, *, json_mode: bool = False, temperature: float = 0.2
) -> str:
    body: dict[str, Any] = {
        "model": settings.openai_chat_model,
        "messages": messages,
        "temperature": temperature,
    }
    if json_mode:
        body["response_format"] = {"type": "json_object"}
    response = requests.post(
        "https://api.openai.com/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {settings.openai_api_key}",
            "Content-Type": "application/json",
        },
        json=body,
        timeout=120,
    )
    response.raise_for_status()
    payload = response.json()
    return str(payload["choices"][0]["message"]["content"]).strip()


def _unavailable(field: str = "result") -> dict[str, Any]:
    return {
        "available": False,
        "status": "UNAVAILABLE",
        field: None,
        "message": "AI features unavailable: OPENAI_API_KEY is not configured.",
    }


async def _chat(messages: list[dict[str, str]], settings: Settings, **kw: Any) -> str:
    return await asyncio.to_thread(lambda: _post_openai_chat(messages, settings, **kw))


def _extract_json(text: str) -> Any:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.strip("`")
        cleaned = cleaned.removeprefix("json").strip()
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        return {}


# --------------------------------------------------------------------------- refinement (speaker labelling)

async def refine_transcript_text(
    text: str | None, translate_to_english: bool, settings: Settings
) -> dict[str, Any]:
    """Reformat a raw transcript into a clean, speaker-labelled Markdown conversation (pragmatic
    diarization via the chat model), optionally translating to English. True acoustic diarization
    (pyannote/Deepgram) would slot in behind this same function later."""
    if not settings.openai_api_key:
        return _unavailable("refined")
    if not text or not text.strip():
        return {"available": True, "status": "EMPTY", "refined": None, "message": "No transcript to refine."}
    clipped = text.strip()[:_REFINE_MAX_CHARS]
    translate_clause = (
        " Then translate the entire conversation into clear, natural English, preserving meaning."
        if translate_to_english
        else ""
    )
    system = (
        "You are an expert transcript editor. You reformat a raw, unpunctuated speech-to-text "
        "transcript into a clean, readable dialogue with speaker labels. You fix obvious transcription "
        "errors, punctuation and capitalisation, and split the text into speaker turns. You NEVER "
        "invent, add, or remove information — only restructure and lightly correct what is present."
    )
    user = (
        "Reformat the following raw transcript into a conversation using Markdown. Put each turn on "
        "its own line, beginning with a bold speaker label (e.g. `**Speaker 1:**`, `**Speaker 2:**`) "
        "followed by that turn's text. Use consistent labels for the same voice. Separate clearly "
        "distinct topics with a Markdown horizontal rule (`---`). Keep it faithful to the source."
        + translate_clause
        + "\n\nRaw transcript:\n\n"
        + clipped
    )
    try:
        refined = await _chat(
            [{"role": "system", "content": system}, {"role": "user", "content": user}], settings
        )
        return {
            "available": True,
            "status": "COMPLETED" if refined else "EMPTY",
            "refined": refined,
            "model": settings.openai_chat_model,
            "translated": translate_to_english,
        }
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "refined": None, "message": str(exc)}


async def assign_speakers_to_segments(
    segments: list[dict[str, Any]], settings: Settings
) -> list[str | None]:
    """Best-effort speaker label per segment (pragmatic diarization). Returns a list aligned to
    `segments`; falls back to all-None on any error so transcription never fails for this."""
    if not settings.openai_api_key or not segments:
        return [None] * len(segments)
    numbered = "\n".join(f"{i}: {s.get('text', '')}" for i, s in enumerate(segments))[:_ANALYSIS_MAX_CHARS]
    system = (
        "You label who is speaking each line of a transcript. Reply with JSON only: "
        '{"labels": ["Speaker 1", "Speaker 1", "Speaker 2", ...]} with exactly one label per input '
        "line, in order. Use stable labels like 'Speaker 1', 'Speaker 2'. Do not add commentary."
    )
    try:
        raw = await _chat(
            [{"role": "system", "content": system}, {"role": "user", "content": numbered}],
            settings,
            json_mode=True,
        )
        labels = _extract_json(raw).get("labels") or []
        out = [str(x) if x else None for x in labels]
        if len(out) < len(segments):
            out += [None] * (len(segments) - len(out))
        return out[: len(segments)]
    except Exception as exc:  # noqa: BLE001 - labelling is best-effort
        logger.info("Speaker labelling failed (non-fatal): %s", exc)
        return [None] * len(segments)


# --------------------------------------------------------------------------- analysis (title / summary / extraction)

_SUMMARY_INSTRUCTIONS = {
    "BRIEF": "Write a concise 2-3 sentence summary of the conversation.",
    "DETAILED": "Write a thorough, well-structured summary in Markdown covering all major points.",
    "BULLETS": "Summarise as a Markdown bulleted list of the key points.",
    "MINUTES": (
        "Write formal meeting minutes in Markdown: attendees (if identifiable), agenda/topics, "
        "decisions, and action items."
    ),
}


async def generate_title(transcript: str, settings: Settings) -> dict[str, Any]:
    if not settings.openai_api_key:
        return _unavailable("title")
    clipped = (transcript or "").strip()[:8000]
    if not clipped:
        return {"available": True, "status": "EMPTY", "title": None}
    try:
        title = await _chat(
            [
                {"role": "system", "content": "You write short, specific titles (max 8 words). Reply with the title only, no quotes."},
                {"role": "user", "content": f"Title for this transcript:\n\n{clipped}"},
            ],
            settings,
            temperature=0.4,
        )
        return {"available": True, "status": "COMPLETED", "title": title.strip().strip('"')}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "title": None, "message": str(exc)}


async def summarize(transcript: str, summary_type: str, settings: Settings) -> dict[str, Any]:
    if not settings.openai_api_key:
        return _unavailable("content")
    instruction = _SUMMARY_INSTRUCTIONS.get(summary_type.upper(), _SUMMARY_INSTRUCTIONS["BRIEF"])
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    if not clipped:
        return {"available": True, "status": "EMPTY", "content": None}
    try:
        content = await _chat(
            [
                {"role": "system", "content": "You are a precise meeting/audio summarizer. Be faithful; never invent facts."},
                {"role": "user", "content": f"{instruction}\n\nTranscript:\n\n{clipped}"},
            ],
            settings,
        )
        return {"available": True, "status": "COMPLETED", "content": content, "model": settings.openai_chat_model}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "content": None, "message": str(exc)}


async def extract_items(transcript: str, settings: Settings) -> dict[str, Any]:
    """One structured pass returning action items, decisions, takeaways, deadlines (with optional
    assignee/dueDate) plus entities, keywords and topics."""
    if not settings.openai_api_key:
        return _unavailable("data")
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    if not clipped:
        return {"available": True, "status": "EMPTY", "data": None}
    system = (
        "You extract structured information from a transcript. Reply with JSON only, using this shape:\n"
        "{\n"
        '  "actionItems": [{"text": str, "assignee": str|null, "dueDate": str|null}],\n'
        '  "decisions": [str],\n'
        '  "takeaways": [str],\n'
        '  "deadlines": [{"text": str, "dueDate": str|null}],\n'
        '  "entities": [str], "keywords": [str], "topics": [str]\n'
        "}\n"
        "dueDate is ISO-8601 (YYYY-MM-DD) when a date is clearly stated, else null. Be faithful; "
        "omit anything not actually present."
    )
    try:
        raw = await _chat(
            [{"role": "system", "content": system}, {"role": "user", "content": clipped}],
            settings,
            json_mode=True,
        )
        data = _extract_json(raw)
        return {"available": True, "status": "COMPLETED", "data": data if isinstance(data, dict) else {}}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "data": None, "message": str(exc)}


async def chat_over_transcript(
    transcript: str, history: list[dict[str, str]], question: str, settings: Settings
) -> dict[str, Any]:
    if not settings.openai_api_key:
        return _unavailable("answer")
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    messages = [
        {
            "role": "system",
            "content": (
                "You answer questions about the following transcript. Use only what it contains; if "
                "the answer isn't present, say so.\n\nTranscript:\n\n" + clipped
            ),
        },
        *history,
        {"role": "user", "content": question},
    ]
    try:
        answer = await _chat(messages, settings, temperature=0.3)
        return {"available": True, "status": "COMPLETED", "answer": answer}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "answer": None, "message": str(exc)}


async def transform_text(
    transcript: str, instruction: str, settings: Settings
) -> dict[str, Any]:
    """Generic transform used by translate / rewrite / tone-conversion / custom-prompt routes."""
    if not settings.openai_api_key:
        return _unavailable("result")
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    if not clipped:
        return {"available": True, "status": "EMPTY", "result": None}
    try:
        result = await _chat(
            [
                {"role": "system", "content": "You transform transcript text exactly as instructed, staying faithful to its meaning."},
                {"role": "user", "content": f"{instruction}\n\nTranscript:\n\n{clipped}"},
            ],
            settings,
            temperature=0.4,
        )
        return {"available": True, "status": "COMPLETED", "result": result}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "result": None, "message": str(exc)}


# --------------------------------------------------------------------------- embeddings (semantic search)

def _post_openai_embeddings(texts: list[str], settings: Settings) -> list[list[float]]:
    response = requests.post(
        "https://api.openai.com/v1/embeddings",
        headers={"Authorization": f"Bearer {settings.openai_api_key}", "Content-Type": "application/json"},
        json={"model": settings.embedding_model, "input": texts},
        timeout=120,
    )
    response.raise_for_status()
    payload = response.json()
    return [item["embedding"] for item in payload.get("data", [])]


async def embed_texts(texts: list[str], settings: Settings) -> list[list[float]] | None:
    """Embed a batch of texts; None when no API key or on failure (caller falls back to keyword)."""
    if not settings.openai_api_key or not texts:
        return None
    try:
        # OpenAI accepts batches; chunk to stay well within limits.
        out: list[list[float]] = []
        for i in range(0, len(texts), 64):
            out += await asyncio.to_thread(_post_openai_embeddings, texts[i : i + 64], settings)
        return out
    except Exception as exc:  # noqa: BLE001
        logger.info("Embedding failed (non-fatal): %s", exc)
        return None


async def embed_query(text: str, settings: Settings) -> list[float] | None:
    res = await embed_texts([text], settings)
    return res[0] if res else None


# --------------------------------------------------------------------------- digests / coaching / analytics / voice

async def generate_digest(summaries_blob: str, period: str, settings: Settings) -> dict[str, Any]:
    if not settings.openai_api_key:
        return _unavailable("content")
    if not summaries_blob.strip():
        return {"available": True, "status": "EMPTY", "content": None}
    try:
        content = await _chat(
            [
                {"role": "system", "content": "You write a crisp productivity digest from a set of recording summaries: highlights, open action items, and what to focus on next. Markdown."},
                {"role": "user", "content": f"Write a {period} digest from these recording summaries:\n\n{summaries_blob[:_ANALYSIS_MAX_CHARS]}"},
            ],
            settings,
        )
        return {"available": True, "status": "COMPLETED", "content": content}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "content": None, "message": str(exc)}


async def meeting_coaching(transcript: str, settings: Settings) -> dict[str, Any]:
    if not settings.openai_api_key:
        return _unavailable("content")
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    if not clipped:
        return {"available": True, "status": "EMPTY", "content": None}
    try:
        content = await _chat(
            [
                {"role": "system", "content": "You are a meeting coach. From a transcript, give constructive feedback: talk-time balance, clarity, follow-through on action items, and 3 concrete improvements. Markdown."},
                {"role": "user", "content": clipped},
            ],
            settings,
        )
        return {"available": True, "status": "COMPLETED", "content": content}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "content": None, "message": str(exc)}


async def conversation_analytics(transcript: str, settings: Settings) -> dict[str, Any]:
    """Structured conversation analytics: per-speaker talk share, sentiment, question count, topics."""
    if not settings.openai_api_key:
        return _unavailable("data")
    clipped = (transcript or "").strip()[:_ANALYSIS_MAX_CHARS]
    if not clipped:
        return {"available": True, "status": "EMPTY", "data": None}
    system = (
        "Analyse the conversation. Reply with JSON only: "
        '{"speakers":[{"name":str,"talkSharePct":number,"sentiment":str}],'
        '"questionsAsked":number,"overallSentiment":str,"topics":[str]}'
    )
    try:
        raw = await _chat([{"role": "system", "content": system}, {"role": "user", "content": clipped}], settings, json_mode=True)
        return {"available": True, "status": "COMPLETED", "data": _extract_json(raw)}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "data": None, "message": str(exc)}


async def voice_command_intent(command: str, settings: Settings) -> dict[str, Any]:
    """Map a free-form voice/text command to a structured intent the client can act on."""
    if not settings.openai_api_key:
        return _unavailable("intent")
    system = (
        "Map the user's command to one intent. Reply with JSON only: "
        '{"intent":"START_RECORDING|STOP_RECORDING|SEARCH|OPEN_LATEST|SUMMARIZE|CREATE_TASK|UNKNOWN",'
        '"query":str|null}'
    )
    try:
        raw = await _chat([{"role": "system", "content": system}, {"role": "user", "content": command}], settings, json_mode=True)
        return {"available": True, "status": "COMPLETED", "intent": _extract_json(raw)}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "intent": None, "message": str(exc)}


async def answer_across(context_blob: str, question: str, settings: Settings) -> dict[str, Any]:
    """Cross-transcript Q&A: answer using a concatenation of relevant transcript snippets."""
    if not settings.openai_api_key:
        return _unavailable("answer")
    messages = [
        {"role": "system", "content": "Answer the question using ONLY the provided excerpts from the user's recordings. Cite which recording when useful. If unknown, say so.\n\nExcerpts:\n\n" + context_blob[:_ANALYSIS_MAX_CHARS]},
        {"role": "user", "content": question},
    ]
    try:
        answer = await _chat(messages, settings, temperature=0.3)
        return {"available": True, "status": "COMPLETED", "answer": answer}
    except requests.RequestException as exc:
        return {"available": True, "status": "FAILED", "answer": None, "message": str(exc)}
