from collections import defaultdict
from datetime import UTC, datetime, timedelta
from typing import Any

from fastapi import APIRouter, Depends

from app.core.db import db
from app.core.deps import get_current_user

router = APIRouter(prefix="/analytics", tags=["analytics"])


@router.get("/overview")
async def overview(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    uid = current_user.id
    recordings = await db.recording.find_many(where={"userId": uid})
    total = len(recordings)
    total_duration = sum((r.durationSec or 0) for r in recordings)
    completed = sum(1 for r in recordings if str(getattr(r.transcriptStatus, "value", r.transcriptStatus)) == "COMPLETED")
    favorites = sum(1 for r in recordings if r.isFavorite)

    rec_ids = [r.id for r in recordings]
    action_total = action_done = 0
    if rec_ids:
        action_total = await db.actionitem.count(where={"recordingId": {"in": rec_ids}, "kind": "ACTION"})
        action_done = await db.actionitem.count(where={"recordingId": {"in": rec_ids}, "kind": "ACTION", "done": True})

    # AI usage proxy: count of completed AI jobs.
    ai_jobs = await db.processingjob.count(where={"requestedById": uid, "status": "COMPLETED"})

    return {
        "totalRecordings": total,
        "totalDurationSec": total_duration,
        "transcribedRecordings": completed,
        "favorites": favorites,
        "actionItemsTotal": action_total,
        "actionItemsCompleted": action_done,
        "aiJobsCompleted": ai_jobs,
    }


@router.get("/timeline")
async def timeline(days: int = 30, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    since = datetime.now(UTC) - timedelta(days=max(1, min(days, 365)))
    recordings = await db.recording.find_many(
        where={"userId": current_user.id, "createdAt": {"gte": since}}, order={"createdAt": "asc"}
    )
    by_day: dict[str, dict[str, int]] = defaultdict(lambda: {"count": 0, "durationSec": 0})
    for r in recordings:
        day = r.createdAt.date().isoformat()
        by_day[day]["count"] += 1
        by_day[day]["durationSec"] += r.durationSec or 0
    return {"days": [{"date": d, **v} for d, v in sorted(by_day.items())]}


@router.get("/summary/{period}")
async def period_summary(period: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    span = 7 if period == "weekly" else 30
    since = datetime.now(UTC) - timedelta(days=span)
    recordings = await db.recording.find_many(where={"userId": current_user.id, "createdAt": {"gte": since}})
    return {
        "period": period,
        "recordings": len(recordings),
        "durationSec": sum((r.durationSec or 0) for r in recordings),
    }
