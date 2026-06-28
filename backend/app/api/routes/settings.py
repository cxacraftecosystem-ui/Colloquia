from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from prisma import Json

from app.core.db import db
from app.core.deps import get_current_user, require_master_admin
from app.schemas.records import SettingsUpdate, UserSettingsUpdate
from app.services.app_settings import (
    VALID_TRANSCRIPTION_MODES,
    get_or_create_app_settings,
    is_valid_hhmm,
)

router = APIRouter(prefix="/settings", tags=["settings"])


@router.get("")
async def get_settings_row(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    row = await get_or_create_app_settings(current_user.id)
    return jsonable_encoder(row)


@router.put("")
async def update_settings_row(payload: SettingsUpdate, current_user: Any = Depends(require_master_admin)) -> dict[str, Any]:
    await get_or_create_app_settings(current_user.id)
    data = {k: v for k, v in payload.model_dump(exclude_unset=True).items() if v is not None}
    if "transcriptionMode" in data and data["transcriptionMode"] not in VALID_TRANSCRIPTION_MODES:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid transcription mode")
    for key in ("batchWindowStart", "batchWindowEnd"):
        if key in data and not is_valid_hhmm(data[key]):
            raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Invalid {key} (HH:MM)")
    data["updatedById"] = current_user.id
    row = await db.appsetting.update(where={"id": "singleton"}, data=data)
    return jsonable_encoder(row)


@router.get("/me")
async def get_my_settings(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    return jsonable_encoder(current_user.settingsJson) or {}


@router.put("/me")
async def update_my_settings(payload: UserSettingsUpdate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    user = await db.user.update(where={"id": current_user.id}, data={"settingsJson": Json(payload.settingsJson)})
    return jsonable_encoder(user.settingsJson) or {}
