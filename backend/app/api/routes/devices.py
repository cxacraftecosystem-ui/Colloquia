from typing import Any

from fastapi import APIRouter, Depends, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import get_current_user
from app.schemas.phase2 import DeviceRegister

router = APIRouter(prefix="/devices", tags=["devices"])


@router.post("", status_code=status.HTTP_201_CREATED)
async def register_device(payload: DeviceRegister, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Register an FCM device token for push. Sends use FCM HTTP v1 via the service account
    (FCM_SERVICE_ACCOUNT_FILE); tokens are stored regardless, so push is a graceful no-op if the
    server credential is absent."""
    row = await db.devicetoken.upsert(
        where={"token": payload.token},
        data={
            "create": {"userId": current_user.id, "token": payload.token, "platform": payload.platform},
            "update": {"userId": current_user.id, "platform": payload.platform},
        },
    )
    return jsonable_encoder(row)


@router.delete("/{token}", status_code=status.HTTP_204_NO_CONTENT)
async def unregister_device(token: str, current_user: Any = Depends(get_current_user)) -> None:
    existing = await db.devicetoken.find_unique(where={"token": token})
    if existing and existing.userId == current_user.id:
        await db.devicetoken.delete(where={"token": token})
