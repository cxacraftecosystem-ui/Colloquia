from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from prisma import Json

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.phase2 import IntegrationUpsert, PushRequest
from app.services import integrations as integ

router = APIRouter(prefix="/integrations", tags=["integrations"])


def _public(row: Any) -> dict[str, Any]:
    data = jsonable_encoder(row)
    # Never leak secret tokens back to the client; report only which keys are set.
    cfg = data.get("config") or {}
    data["config"] = {"keys": sorted(cfg.keys())} if isinstance(cfg, dict) else {}
    return data


@router.get("")
async def list_integrations(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    rows = await db.integration.find_many(where={"userId": current_user.id})
    return [_public(r) for r in rows]


@router.put("")
async def upsert_integration(payload: IntegrationUpsert, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    provider = payload.provider.upper()
    if provider not in integ.SUPPORTED_PROVIDERS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Unsupported provider {provider}")
    row = await db.integration.upsert(
        where={"userId_provider": {"userId": current_user.id, "provider": provider}},
        data={
            "create": {"userId": current_user.id, "provider": provider, "config": Json(payload.config or {}), "enabled": payload.enabled},
            "update": {"config": Json(payload.config or {}), "enabled": payload.enabled},
        },
    )
    return _public(row)


@router.delete("/{provider}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_integration(provider: str, current_user: Any = Depends(get_current_user)) -> None:
    existing = await db.integration.find_unique(
        where={"userId_provider": {"userId": current_user.id, "provider": provider.upper()}}
    )
    if existing:
        await db.integration.delete(where={"id": existing.id})


@router.post("/push/{recording_id}")
async def push_recording(recording_id: str, payload: PushRequest, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    provider = payload.provider.upper()
    integration = await db.integration.find_unique(
        where={"userId_provider": {"userId": current_user.id, "provider": provider}}
    )
    if not integration:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"Connect {provider} first")
    summary = await db.summary.find_first(where={"recordingId": recording_id, "type": "BRIEF"})
    actions = await db.actionitem.find_many(where={"recordingId": recording_id, "kind": "ACTION"})
    body = {
        "title": rec.aiTitle or rec.title,
        "summary": summary.content if summary else "",
        "actionItems": [a.text for a in actions],
    }
    return integ.push_to_provider(provider, integration.config, body)
