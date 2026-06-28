from typing import Any

from fastapi import APIRouter, Depends, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import get_current_user, require_master_admin
from app.schemas.app_release import AppReleasePublishRequest
from app.services.s3 import presign_get_url, public_url_for_key

router = APIRouter(prefix="/app", tags=["app"])


@router.post("/release", status_code=status.HTTP_201_CREATED)
async def publish_release(
    payload: AppReleasePublishRequest,
    current_user: Any = Depends(require_master_admin),
) -> dict[str, Any]:
    """Master-admin only: record a new app release for OTA "push update to all". The APK has already
    been uploaded to object storage (via /media/presign) by the publishing device; this stores the
    version metadata so other devices discover and self-install it.

    The download URL is critical: other devices only show the update prompt when the release carries a
    non-blank URL, so derive it from the object key if the client didn't send one — a release must
    never be published without a way to download it, or the push silently does nothing."""
    url = payload.url or public_url_for_key(payload.objectKey)
    created = await db.apprelease.create(
        data={
            "versionCode": payload.versionCode,
            "versionName": payload.versionName,
            "objectKey": payload.objectKey,
            "url": url,
            "notes": payload.notes,
            "publishedById": current_user.id,
        }
    )
    return jsonable_encoder(created)


@router.get("/release/latest")
async def latest_release(_: Any = Depends(get_current_user)) -> dict[str, Any]:
    """The current release (highest versionCode), or an empty object when none has been published.
    Backfills the download URL from the object key if an older row was saved without one."""
    release = await db.apprelease.find_first(order=[{"versionCode": "desc"}, {"publishedAt": "desc"}])
    if not release:
        return {}
    data = jsonable_encoder(release)
    if not data.get("url") and data.get("objectKey"):
        data["url"] = public_url_for_key(data["objectKey"])
    # The bucket is private, so a bare public URL 403s — always hand the client a short-lived presigned
    # GET it can actually download from. The publishing device uploaded the APK under its own object key.
    if data.get("objectKey"):
        try:
            data["downloadUrl"] = presign_get_url(data["objectKey"], expires=3600)
        except Exception:  # noqa: BLE001 - never fail the version check just because signing hiccuped
            data["downloadUrl"] = data.get("url")
    return data
