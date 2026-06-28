import re
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import Response

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.services.export import EXPORTERS

router = APIRouter(prefix="/recordings", tags=["export"])


def _safe(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", name).strip("_") or "recording"


@router.get("/{recording_id}/export.{fmt}")
async def export_recording(recording_id: str, fmt: str, current_user: Any = Depends(get_current_user)) -> Response:
    fmt = fmt.lower()
    if fmt not in EXPORTERS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Unsupported format")
    recording = await db.recording.find_unique(where={"id": recording_id})
    if not recording:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(recording, current_user)

    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    segments = []
    if transcript:
        segments = await db.transcriptsegment.find_many(where={"transcriptId": transcript.id}, order={"idx": "asc"})

    media_type, builder = EXPORTERS[fmt]
    body = builder(recording, transcript, segments)
    filename = f"{_safe(recording.title or 'recording')}.{fmt}"
    return Response(
        content=body,
        media_type=media_type,
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )
