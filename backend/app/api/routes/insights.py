from datetime import UTC, datetime, timedelta
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.services import ai as ai_service

router = APIRouter(tags=["insights"])


async def _transcript_text(recording_id: str) -> str:
    t = await db.transcript.find_unique(where={"recordingId": recording_id})
    return (t.refinedText or t.rawText or "") if t else ""


@router.get("/insights/digest")
async def digest(period: str = "weekly", current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """AI daily/weekly digest over the user's recent summaries. Persists the digest."""
    span = 1 if period == "daily" else 7
    start = datetime.now(UTC) - timedelta(days=span)
    recs = await db.recording.find_many(where={"userId": current_user.id, "createdAt": {"gte": start}})
    rec_ids = [r.id for r in recs]
    blob_parts = []
    if rec_ids:
        summaries = await db.summary.find_many(where={"recordingId": {"in": rec_ids}, "type": "BRIEF"})
        title_by_id = {r.id: (r.aiTitle or r.title) for r in recs}
        for s in summaries:
            blob_parts.append(f"[{title_by_id.get(s.recordingId, '')}] {s.content}")
    res = await ai_service.generate_digest("\n\n".join(blob_parts), period, get_settings())
    if res.get("status") == "COMPLETED" and res.get("content"):
        await db.digest.create(
            data={"userId": current_user.id, "type": period.upper(), "content": res["content"], "periodStart": start, "periodEnd": datetime.now(UTC)}
        )
    return res


@router.get("/recordings/{recording_id}/coaching")
async def coaching(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    return await ai_service.meeting_coaching(await _transcript_text(recording_id), get_settings())


@router.get("/recordings/{recording_id}/conversation-analytics")
async def conversation_analytics(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    return await ai_service.conversation_analytics(await _transcript_text(recording_id), get_settings())


@router.get("/insights/productivity")
async def productivity_insights(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Lightweight, no-AI productivity numbers (action-item completion, cadence)."""
    recs = await db.recording.find_many(where={"userId": current_user.id})
    rec_ids = [r.id for r in recs]
    total_actions = done_actions = 0
    if rec_ids:
        total_actions = await db.actionitem.count(where={"recordingId": {"in": rec_ids}, "kind": "ACTION"})
        done_actions = await db.actionitem.count(where={"recordingId": {"in": rec_ids}, "kind": "ACTION", "done": True})
    week_ago = datetime.now(UTC) - timedelta(days=7)
    this_week = sum(1 for r in recs if r.createdAt >= week_ago)
    return {
        "recordings": len(recs),
        "recordingsThisWeek": this_week,
        "actionItemsTotal": total_actions,
        "actionItemsCompleted": done_actions,
        "completionRate": round(done_actions / total_actions, 2) if total_actions else 0.0,
    }
