from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from prisma import Json

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.phase2 import RuleUpsert
from app.services import automation as automation_service

router = APIRouter(prefix="/automation", tags=["automation"])


@router.get("/rules")
async def list_rules(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    return jsonable_encoder(await db.automationrule.find_many(where={"userId": current_user.id}, order={"createdAt": "desc"}))


@router.post("/rules", status_code=status.HTTP_201_CREATED)
async def create_rule(payload: RuleUpsert, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rule = await db.automationrule.create(
        data={
            "userId": current_user.id,
            "name": payload.name,
            "trigger": payload.trigger,
            "action": payload.action,
            "config": Json(payload.config or {}),
            "enabled": payload.enabled,
        }
    )
    return jsonable_encoder(rule)


@router.delete("/rules/{rule_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_rule(rule_id: str, current_user: Any = Depends(get_current_user)) -> None:
    rule = await db.automationrule.find_unique(where={"id": rule_id})
    if not rule:
        return
    assert_owns(rule, current_user)
    await db.automationrule.delete(where={"id": rule_id})


@router.post("/rules/run/{recording_id}")
async def run_rules_now(recording_id: str, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    results = await automation_service.run_rules_for_recording(recording_id, "ON_ANALYZED")
    return {"results": results}
