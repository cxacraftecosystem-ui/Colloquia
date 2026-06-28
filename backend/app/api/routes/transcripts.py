from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.records import ManualTranscript, SegmentEdit
from app.services.media_queue import transcribe_recording_now

router = APIRouter(prefix="/recordings", tags=["transcripts"])


async def _owned(recording_id: str, current_user: Any) -> Any:
    recording = await db.recording.find_unique(where={"id": recording_id})
    if not recording:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(recording, current_user)
    return recording


@router.get("/{recording_id}/transcript")
async def get_transcript(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    transcript = await db.transcript.find_unique(
        where={"recordingId": recording_id},
        include={"segments": {"order_by": {"idx": "asc"}}},
    )
    if not transcript:
        return {"status": "PENDING", "segments": [], "rawText": None, "refinedText": None}
    from fastapi.encoders import jsonable_encoder

    return jsonable_encoder(transcript)


@router.get("/{recording_id}/transcript/search")
async def search_in_transcript(recording_id: str, q: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    if not transcript:
        return {"matches": []}
    needle = q.strip().lower()
    if not needle:
        return {"matches": []}
    segments = await db.transcriptsegment.find_many(
        where={"transcriptId": transcript.id}, order={"idx": "asc"}
    )
    matches = [
        {"idx": s.idx, "startMs": s.startMs, "speaker": s.speaker, "text": s.editedText or s.text}
        for s in segments
        if needle in (s.editedText or s.text or "").lower()
    ]
    return {"matches": matches, "count": len(matches)}


@router.patch("/{recording_id}/transcript/segments/{idx}")
async def edit_segment(recording_id: str, idx: int, payload: SegmentEdit, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    if not transcript:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="No transcript yet")
    segment = await db.transcriptsegment.find_first(where={"transcriptId": transcript.id, "idx": idx})
    if not segment:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Segment not found")
    updated = await db.transcriptsegment.update(where={"id": segment.id}, data={"editedText": payload.text})
    from fastapi.encoders import jsonable_encoder

    return jsonable_encoder(updated)


@router.post("/{recording_id}/transcribe-now")
async def transcribe_now(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    recording = await _owned(recording_id, current_user)
    result = await transcribe_recording_now(recording, get_settings())
    return result


@router.post("/{recording_id}/transcript")
async def set_manual_transcript(recording_id: str, payload: ManualTranscript, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Manually set/correct the raw transcript text (no segments)."""
    await _owned(recording_id, current_user)
    transcript = await db.transcript.upsert(
        where={"recordingId": recording_id},
        data={
            "create": {"recordingId": recording_id, "status": "COMPLETED", "rawText": payload.rawText},
            "update": {"status": "COMPLETED", "rawText": payload.rawText},
        },
    )
    await db.recording.update(where={"id": recording_id}, data={"transcriptStatus": "COMPLETED", "status": "COMPLETED"})
    from fastapi.encoders import jsonable_encoder

    return jsonable_encoder(transcript)
