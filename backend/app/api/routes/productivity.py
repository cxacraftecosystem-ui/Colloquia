from datetime import UTC, datetime, timedelta
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.phase2 import GoogleActionRequest
from app.services import google_productivity as gp

router = APIRouter(prefix="/recordings", tags=["productivity"])


async def _owned(recording_id: str, current_user: Any) -> Any:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    return rec


@router.post("/{recording_id}/google/calendar")
async def add_calendar_events(recording_id: str, payload: GoogleActionRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Create a Google Calendar event for each deadline action item. Needs a Google access token with
    the calendar.events scope (the app requests it at sign-in)."""
    rec = await _owned(recording_id, current_user)
    if not payload.googleAccessToken:
        return {"status": "NEEDS_AUTH", "scope": "https://www.googleapis.com/auth/calendar.events"}
    deadlines = await db.actionitem.find_many(where={"recordingId": recording_id, "kind": {"in": ["DEADLINE", "ACTION"]}})
    created = []
    for d in deadlines:
        due = d.dueDate or (datetime.now(UTC) + timedelta(days=1))
        start = due.isoformat()
        end = (due + timedelta(hours=1)).isoformat()
        res = gp.create_calendar_event(payload.googleAccessToken, f"{rec.title}: {d.text[:80]}", d.text, start, end)
        created.append(res)
    return {"status": "DONE", "created": created}


@router.post("/{recording_id}/google/tasks")
async def add_tasks(recording_id: str, payload: GoogleActionRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await _owned(recording_id, current_user)
    if not payload.googleAccessToken:
        return {"status": "NEEDS_AUTH", "scope": "https://www.googleapis.com/auth/tasks"}
    items = await db.actionitem.find_many(where={"recordingId": recording_id, "kind": "ACTION"})
    created = []
    for it in items:
        due = it.dueDate.isoformat() if it.dueDate else None
        created.append(gp.create_task(payload.googleAccessToken, it.text[:200], f"From: {rec.title}", due))
    return {"status": "DONE", "created": created}


@router.post("/{recording_id}/google/gmail-draft")
async def gmail_draft(recording_id: str, payload: GoogleActionRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await _owned(recording_id, current_user)
    if not payload.googleAccessToken:
        return {"status": "NEEDS_AUTH", "scope": "https://www.googleapis.com/auth/gmail.compose"}
    summary = await db.summary.find_first(where={"recordingId": recording_id, "type": "DETAILED"})
    actions = await db.actionitem.find_many(where={"recordingId": recording_id, "kind": "ACTION"})
    body = (summary.content if summary else "") + "\n\nAction items:\n" + "\n".join(f"- {a.text}" for a in actions)
    return gp.create_gmail_draft(payload.googleAccessToken, payload.to or "", f"Notes: {rec.title}", body)
