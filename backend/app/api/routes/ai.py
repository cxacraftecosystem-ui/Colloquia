from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.records import ChatRequest, SummaryRegenerate, TransformRequest
from app.services import ai as ai_service

router = APIRouter(prefix="/recordings", tags=["ai"])

VALID_SUMMARY_TYPES = {"BRIEF", "DETAILED", "BULLETS", "MINUTES"}


def _ensure_ai_ok(res: dict[str, Any]) -> dict[str, Any]:
    """Turn a non-success AI result into a clean HTTP error so the client never has to parse a
    `{"status":"FAILED","content":null,...}` body (which crashes strict deserialization) and instead
    shows a friendly message. Rate-limits map to 429 so the app can say "try again in a moment"."""
    state = str(res.get("status") or "").upper()
    if state == "COMPLETED":
        return res
    if state == "QUOTA":
        raise HTTPException(
            status_code=status.HTTP_402_PAYMENT_REQUIRED,
            detail=res.get("message") or "AI features are unavailable: the OpenAI account is out of quota/credits.",
        )
    if state == "RATE_LIMITED":
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="The AI service is busy right now. Please try again in a moment.",
        )
    if state == "UNAVAILABLE":
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=res.get("message") or "AI features are not configured.",
        )
    if state == "EMPTY":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="There isn't any transcript text to work with yet.",
        )
    raise HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail=res.get("message") or "The AI request failed. Please try again.",
    )


async def _owned(recording_id: str, current_user: Any) -> Any:
    recording = await db.recording.find_unique(where={"id": recording_id})
    if not recording:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(recording, current_user)
    return recording


async def _transcript_text(recording_id: str) -> str:
    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    if not transcript:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="No transcript available yet")
    return transcript.translatedText or transcript.refinedText or transcript.rawText or ""


# --------------------------------------------------------------------------- summaries

@router.get("/{recording_id}/summaries")
async def list_summaries(recording_id: str, current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    await _owned(recording_id, current_user)
    return jsonable_encoder(await db.summary.find_many(where={"recordingId": recording_id}))


@router.post("/{recording_id}/summaries/regenerate")
async def regenerate_summary(recording_id: str, payload: SummaryRegenerate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    stype = payload.type.upper()
    if stype not in VALID_SUMMARY_TYPES:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid summary type")
    text = await _transcript_text(recording_id)
    res = await ai_service.summarize(text, stype, get_settings())
    _ensure_ai_ok(res)
    summary = await db.summary.upsert(
        where={"recordingId_type": {"recordingId": recording_id, "type": stype}},
        data={
            "create": {"recordingId": recording_id, "type": stype, "content": res["content"], "model": res.get("model")},
            "update": {"content": res["content"], "model": res.get("model")},
        },
    )
    return jsonable_encoder(summary)


# --------------------------------------------------------------------------- action items

@router.get("/{recording_id}/action-items")
async def list_action_items(recording_id: str, current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    await _owned(recording_id, current_user)
    return jsonable_encoder(await db.actionitem.find_many(where={"recordingId": recording_id}, order={"createdAt": "asc"}))


@router.post("/{recording_id}/action-items/{item_id}/toggle")
async def toggle_action_item(recording_id: str, item_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    item = await db.actionitem.find_unique(where={"id": item_id})
    if not item or item.recordingId != recording_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Action item not found")
    updated = await db.actionitem.update(where={"id": item_id}, data={"done": not item.done})
    return jsonable_encoder(updated)


@router.get("/{recording_id}/extracted")
async def list_extracted(recording_id: str, current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    await _owned(recording_id, current_user)
    return jsonable_encoder(await db.extracteditem.find_many(where={"recordingId": recording_id}))


# --------------------------------------------------------------------------- chat

@router.get("/{recording_id}/chat")
async def chat_history(recording_id: str, current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    await _owned(recording_id, current_user)
    return jsonable_encoder(await db.chatmessage.find_many(where={"recordingId": recording_id}, order={"createdAt": "asc"}))


@router.post("/{recording_id}/chat")
async def chat(recording_id: str, payload: ChatRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    text = await _transcript_text(recording_id)
    history_rows = await db.chatmessage.find_many(where={"recordingId": recording_id}, order={"createdAt": "asc"}, take=20)
    history = [{"role": r.role.value.lower() if hasattr(r.role, "value") else str(r.role).lower(), "content": r.content} for r in history_rows]

    await db.chatmessage.create(data={"recordingId": recording_id, "role": "USER", "content": payload.question})
    res = await ai_service.chat_over_transcript(text, history, payload.question, get_settings())
    _ensure_ai_ok(res)
    msg = await db.chatmessage.create(
        data={"recordingId": recording_id, "role": "ASSISTANT", "content": res["answer"]}
    )
    return jsonable_encoder(msg)


# --------------------------------------------------------------------------- transforms (title / translate / rewrite / tone / custom)

@router.post("/{recording_id}/title")
async def regenerate_title(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    text = await _transcript_text(recording_id)
    res = await ai_service.generate_title(text, get_settings())
    _ensure_ai_ok(res)
    if res.get("title"):
        await db.recording.update(where={"id": recording_id}, data={"aiTitle": res["title"]})
    return res


@router.post("/{recording_id}/translate")
async def translate(recording_id: str, payload: TransformRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    text = await _transcript_text(recording_id)
    lang = payload.targetLanguage or "English"
    return _ensure_ai_ok(await ai_service.transform_text(text, f"Translate the transcript into {lang}. Keep speaker labels.", get_settings()))


@router.post("/{recording_id}/rewrite")
async def rewrite(recording_id: str, payload: TransformRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    text = await _transcript_text(recording_id)
    instruction = payload.instruction or "Rewrite the transcript as clean, well-structured prose."
    return _ensure_ai_ok(await ai_service.transform_text(text, instruction, get_settings()))


@router.post("/{recording_id}/tone")
async def convert_tone(recording_id: str, payload: TransformRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    text = await _transcript_text(recording_id)
    tone = payload.tone or "professional"
    return _ensure_ai_ok(await ai_service.transform_text(text, f"Rewrite the transcript content in a {tone} tone.", get_settings()))


@router.post("/{recording_id}/custom")
async def custom_prompt(recording_id: str, payload: TransformRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    if not payload.instruction:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="instruction required")
    text = await _transcript_text(recording_id)
    return _ensure_ai_ok(await ai_service.transform_text(text, payload.instruction, get_settings()))
