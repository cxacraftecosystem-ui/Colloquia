from typing import Any

from fastapi import APIRouter, Depends, Query
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import get_current_user

router = APIRouter(prefix="/search", tags=["search"])


@router.get("")
async def global_search(
    q: str,
    current_user: Any = Depends(get_current_user),
    speaker: str | None = None,
    tagId: str | None = None,
    folderId: str | None = None,
    limit: int = Query(30, ge=1, le=100),
) -> dict[str, Any]:
    """Search the user's recordings by title/transcript text, optionally filtered by speaker, tag,
    folder. Returns recordings plus the matching transcript snippets. (Semantic/cross-transcript
    search is a documented later phase.)"""
    needle = q.strip()
    where: dict[str, Any] = {"userId": current_user.id}
    if tagId:
        where["tags"] = {"some": {"tagId": tagId}}
    if folderId:
        where["folderId"] = folderId
    if needle:
        where["OR"] = [
            {"title": {"contains": needle, "mode": "insensitive"}},
            {"aiTitle": {"contains": needle, "mode": "insensitive"}},
            {"transcript": {"rawText": {"contains": needle, "mode": "insensitive"}}},
            {"transcript": {"refinedText": {"contains": needle, "mode": "insensitive"}}},
        ]
    recordings = await db.recording.find_many(
        where=where,
        include={"folder": True, "tags": {"include": {"tag": True}}},
        order={"createdAt": "desc"},
        take=limit,
    )

    # Pull matching transcript snippets (and apply the speaker filter, if any).
    results: list[dict[str, Any]] = []
    for rec in recordings:
        transcript = await db.transcript.find_unique(where={"recordingId": rec.id})
        snippets: list[dict[str, Any]] = []
        if transcript:
            seg_where: dict[str, Any] = {"transcriptId": transcript.id}
            if speaker:
                seg_where["speaker"] = {"contains": speaker, "mode": "insensitive"}
            if needle:
                seg_where["OR"] = [
                    {"text": {"contains": needle, "mode": "insensitive"}},
                    {"editedText": {"contains": needle, "mode": "insensitive"}},
                ]
            segs = await db.transcriptsegment.find_many(where=seg_where, order={"idx": "asc"}, take=5)
            snippets = [
                {"idx": s.idx, "startMs": s.startMs, "speaker": s.speaker, "text": s.editedText or s.text}
                for s in segs
            ]
        if speaker and not snippets:
            continue
        results.append({"recording": jsonable_encoder(rec), "snippets": snippets})

    return {"query": needle, "count": len(results), "results": results}
