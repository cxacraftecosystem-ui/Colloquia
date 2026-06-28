from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from prisma import Json

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.phase2 import TemplateCreate
from app.services import ai as ai_service

router = APIRouter(prefix="/templates", tags=["templates"])


@router.get("")
async def list_templates(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    return jsonable_encoder(await db.meetingtemplate.find_many(where={"userId": current_user.id}, order={"createdAt": "desc"}))


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_template(payload: TemplateCreate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    t = await db.meetingtemplate.create(data={"userId": current_user.id, "name": payload.name, "prompts": Json(payload.prompts)})
    return jsonable_encoder(t)


@router.delete("/{template_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_template(template_id: str, current_user: Any = Depends(get_current_user)) -> None:
    t = await db.meetingtemplate.find_unique(where={"id": template_id})
    if not t:
        return
    assert_owns(t, current_user)
    await db.meetingtemplate.delete(where={"id": template_id})


@router.post("/{template_id}/apply/{recording_id}")
async def apply_template(template_id: str, recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Run each prompt in the template against the recording's transcript and return the results."""
    template = await db.meetingtemplate.find_unique(where={"id": template_id})
    if not template:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Template not found")
    assert_owns(template, current_user)
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    text = (transcript.refinedText or transcript.rawText or "") if transcript else ""
    if not text.strip():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="No transcript yet")
    settings = get_settings()
    prompts = template.prompts if isinstance(template.prompts, list) else []
    sections = []
    for p in prompts:
        label = p.get("label") if isinstance(p, dict) else None
        instruction = p.get("instruction") if isinstance(p, dict) else None
        if not instruction:
            continue
        res = await ai_service.transform_text(text, instruction, settings)
        sections.append({"label": label or "Section", "content": res.get("result"), "status": res.get("status")})
    return {"template": template.name, "sections": sections}
