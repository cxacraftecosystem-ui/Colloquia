from datetime import UTC, datetime
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.records import RecordingUpdate, TagAssign
from app.services.pagination import normalize_pagination, page_payload
from app.services.s3 import delete_object

router = APIRouter(prefix="/recordings", tags=["recordings"])

LIST_INCLUDE = {"folder": True, "tags": {"include": {"tag": True}}}
DETAIL_INCLUDE = {
    "folder": True,
    "tags": {"include": {"tag": True}},
    "transcript": {"include": {"segments": {"order_by": {"idx": "asc"}}}},
    "summaries": True,
    "actionItems": {"order_by": {"createdAt": "asc"}},
    "extractedItems": True,
}

SORT_FIELDS = {
    "recent": ("createdAt", "desc"),
    "oldest": ("createdAt", "asc"),
    "title": ("title", "asc"),
    "duration": ("durationSec", "desc"),
    "viewed": ("lastViewedAt", "desc"),
}


def _serialize(value: Any) -> Any:
    return jsonable_encoder(value)


async def _owned(recording_id: str, current_user: Any, include: dict | None = None) -> Any:
    recording = await db.recording.find_unique(where={"id": recording_id}, include=include)
    if not recording:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(recording, current_user)
    return recording


@router.get("")
async def list_recordings(
    current_user: Any = Depends(get_current_user),
    search: str | None = None,
    folderId: str | None = None,
    tagId: str | None = None,
    favorite: bool | None = None,
    archived: bool | None = None,
    pinned: bool | None = None,
    statusFilter: str | None = None,
    sort: str = "recent",
    dateFrom: datetime | None = None,
    dateTo: datetime | None = None,
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=100),
) -> dict[str, Any]:
    page, page_size, skip = normalize_pagination(page, pageSize)
    where: dict[str, Any] = {"userId": current_user.id}
    # By default hide archived unless explicitly asked for.
    where["isArchived"] = bool(archived) if archived is not None else False
    if favorite is not None:
        where["isFavorite"] = favorite
    if pinned is not None:
        where["isPinned"] = pinned
    if folderId:
        where["folderId"] = folderId
    if statusFilter:
        where["transcriptStatus"] = statusFilter
    if tagId:
        where["tags"] = {"some": {"tagId": tagId}}
    if search:
        where["OR"] = [
            {"title": {"contains": search, "mode": "insensitive"}},
            {"aiTitle": {"contains": search, "mode": "insensitive"}},
            {"transcript": {"rawText": {"contains": search, "mode": "insensitive"}}},
        ]
    if dateFrom or dateTo:
        rng: dict[str, Any] = {}
        if dateFrom:
            rng["gte"] = dateFrom
        if dateTo:
            rng["lte"] = dateTo
        where["createdAt"] = rng

    field, direction = SORT_FIELDS.get(sort, SORT_FIELDS["recent"])
    total = await db.recording.count(where=where)
    items = await db.recording.find_many(
        where=where, include=LIST_INCLUDE, skip=skip, take=page_size, order={field: direction}
    )
    # Pinned first within the page for the default sort.
    return page_payload(_serialize(items), total, page, page_size)


@router.get("/{recording_id}")
async def get_recording(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    recording = await _owned(recording_id, current_user, DETAIL_INCLUDE)
    await db.recording.update(where={"id": recording_id}, data={"lastViewedAt": datetime.now(UTC)})
    return _serialize(recording)


@router.patch("/{recording_id}")
async def update_recording(recording_id: str, payload: RecordingUpdate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    data = {k: v for k, v in payload.model_dump(exclude_unset=True).items() if v is not None}
    if not data:
        return _serialize(await db.recording.find_unique(where={"id": recording_id}, include=LIST_INCLUDE))
    updated = await db.recording.update(where={"id": recording_id}, data=data, include=LIST_INCLUDE)
    return _serialize(updated)


@router.post("/{recording_id}/favorite")
async def toggle_favorite(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await _owned(recording_id, current_user)
    updated = await db.recording.update(where={"id": recording_id}, data={"isFavorite": not rec.isFavorite})
    return _serialize(updated)


@router.post("/{recording_id}/archive")
async def toggle_archive(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await _owned(recording_id, current_user)
    updated = await db.recording.update(where={"id": recording_id}, data={"isArchived": not rec.isArchived})
    return _serialize(updated)


@router.post("/{recording_id}/pin")
async def toggle_pin(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await _owned(recording_id, current_user)
    updated = await db.recording.update(where={"id": recording_id}, data={"isPinned": not rec.isPinned})
    return _serialize(updated)


@router.put("/{recording_id}/tags")
async def set_tags(recording_id: str, payload: TagAssign, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    await _owned(recording_id, current_user)
    await db.recordingtag.delete_many(where={"recordingId": recording_id})
    for tag_id in payload.tagIds:
        tag = await db.tag.find_unique(where={"id": tag_id})
        if tag and tag.userId == current_user.id:
            await db.recordingtag.create(data={"recordingId": recording_id, "tagId": tag_id})
    return _serialize(await db.recording.find_unique(where={"id": recording_id}, include=LIST_INCLUDE))


@router.delete("/{recording_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_recording(recording_id: str, current_user: Any = Depends(get_current_user)) -> None:
    rec = await _owned(recording_id, current_user)
    # Best-effort: remove the audio object from storage, then the row (cascades transcript/segments/etc).
    if rec.objectKey:
        try:
            delete_object(rec.objectKey)
        except Exception:  # noqa: BLE001 - never block deletion on a storage hiccup
            pass
    await db.recording.delete(where={"id": recording_id})
