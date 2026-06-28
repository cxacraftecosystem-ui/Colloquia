import asyncio
import math
from datetime import datetime
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import get_current_user
from app.schemas.media import (
    CompleteUploadRequest,
    MultipartAbortRequest,
    MultipartCompleteRequest,
    MultipartCreateRequest,
    MultipartCreateResponse,
    MultipartPresignPartsRequest,
    MultipartPresignPartsResponse,
    PresignRequest,
    PresignResponse,
)
from app.services.media_queue import TRANSCRIPTION, enqueue_job
from app.services.s3 import (
    abort_multipart_upload,
    complete_multipart_upload,
    create_multipart_upload,
    make_object_key,
    presign_put_url,
    presign_upload_part,
    public_url_for_key,
)

router = APIRouter(prefix="/media", tags=["media"])

# 64 MB parts: large enough to keep part counts low, small enough to retry cheaply.
MULTIPART_PART_SIZE = 64 * 1024 * 1024


@router.post("/presign", response_model=PresignResponse)
async def presign_upload(payload: PresignRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    settings = get_settings()
    object_key = make_object_key(current_user.id, payload.filename)
    return {
        "uploadUrl": presign_put_url(object_key, payload.mimeType),
        "method": "PUT",
        "objectKey": object_key,
        "bucket": settings.aws_s3_bucket,
        "headers": {"Content-Type": payload.mimeType},
        "publicUrl": public_url_for_key(object_key),
    }


def _assert_owns_object(object_key: str, current_user: Any) -> None:
    if not object_key.startswith(f"media/{current_user.id}/"):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="You can only manage your own uploads")


@router.post("/multipart/create", response_model=MultipartCreateResponse)
async def create_multipart(payload: MultipartCreateRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    settings = get_settings()
    object_key = make_object_key(current_user.id, payload.filename)
    upload_id = await asyncio.to_thread(create_multipart_upload, object_key, payload.mimeType)
    part_count = max(1, math.ceil(payload.sizeBytes / MULTIPART_PART_SIZE))
    return {
        "objectKey": object_key,
        "uploadId": upload_id,
        "bucket": settings.aws_s3_bucket,
        "partSize": MULTIPART_PART_SIZE,
        "partCount": part_count,
        "publicUrl": public_url_for_key(object_key),
    }


@router.post("/multipart/presign-parts", response_model=MultipartPresignPartsResponse)
async def presign_multipart_parts(payload: MultipartPresignPartsRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    _assert_owns_object(payload.objectKey, current_user)
    urls: dict[str, str] = {}
    for part_number in payload.partNumbers:
        urls[str(part_number)] = await asyncio.to_thread(
            presign_upload_part, payload.objectKey, payload.uploadId, part_number
        )
    return {"urls": urls}


@router.post("/multipart/complete")
async def complete_multipart(payload: MultipartCompleteRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    _assert_owns_object(payload.objectKey, current_user)
    settings = get_settings()
    parts = sorted(
        ({"PartNumber": p.partNumber, "ETag": p.etag} for p in payload.parts),
        key=lambda item: item["PartNumber"],
    )
    if not parts:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="No parts to complete")
    await asyncio.to_thread(complete_multipart_upload, payload.objectKey, payload.uploadId, parts)
    return {"objectKey": payload.objectKey, "bucket": settings.aws_s3_bucket, "publicUrl": public_url_for_key(payload.objectKey)}


@router.post("/multipart/abort")
async def abort_multipart(payload: MultipartAbortRequest, current_user: Any = Depends(get_current_user)) -> dict[str, bool]:
    _assert_owns_object(payload.objectKey, current_user)
    await asyncio.to_thread(abort_multipart_upload, payload.objectKey, payload.uploadId)
    return {"aborted": True}


def _serialize(recording: Any) -> dict[str, Any]:
    return jsonable_encoder(recording)


@router.post("/complete", status_code=status.HTTP_201_CREATED)
async def complete_upload(payload: CompleteUploadRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Create the Recording row for an uploaded object and (for audio) enqueue transcription.

    Idempotent on objectKey: a retried/timed-out call returns the existing row and enqueues its job
    once if the original request died before it could — so a retry never drops transcription. Mirrors
    the field-repo /media/complete contract.
    """
    settings = get_settings()
    _assert_owns_object(payload.objectKey, current_user)

    existing = await db.recording.find_unique(where={"objectKey": payload.objectKey}, include={"jobs": True})
    if existing is not None:
        if payload.transcribe and not (existing.jobs or []):
            await enqueue_job(existing.id, TRANSCRIPTION, current_user.id, settings)
            existing = await db.recording.find_unique(where={"id": existing.id})
        return _serialize(existing)

    recorded_at = None
    if payload.recordedAt:
        try:
            recorded_at = datetime.fromisoformat(payload.recordedAt.replace("Z", "+00:00"))
        except ValueError:
            recorded_at = None

    title = payload.title or (payload.originalFilename or "Recording").rsplit(".", 1)[0]
    data: dict[str, Any] = {
        "userId": current_user.id,
        "title": title,
        "objectKey": payload.objectKey,
        "bucket": payload.bucket or settings.aws_s3_bucket,
        "url": payload.url or public_url_for_key(payload.objectKey),
        "mimeType": payload.mimeType,
        "originalFilename": payload.originalFilename,
        "sizeBytes": payload.sizeBytes,
        "durationSec": payload.durationSec,
        "status": "UPLOADED",
        "transcriptStatus": "PENDING",
    }
    if payload.folderId:
        data["folderId"] = payload.folderId
    if recorded_at:
        data["recordedAt"] = recorded_at

    try:
        created = await db.recording.create(data=data)
    except Exception:
        # Lost a race with a concurrent retry — return the row that won.
        racer = await db.recording.find_unique(where={"objectKey": payload.objectKey})
        if racer is not None:
            return _serialize(racer)
        raise

    if payload.transcribe:
        await enqueue_job(created.id, TRANSCRIPTION, current_user.id, settings)
        created = await db.recording.find_unique(where={"id": created.id})
    return _serialize(created)
