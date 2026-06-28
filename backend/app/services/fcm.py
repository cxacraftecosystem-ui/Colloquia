"""Push notifications via the FCM HTTP v1 API.

The legacy FCM "server key" is decommissioned, so this uses a Firebase **service-account** JSON to mint
a short-lived OAuth access token and POSTs to the v1 endpoint. Configure FCM_SERVICE_ACCOUNT_FILE to the
JSON path; absent -> push is a graceful no-op (tokens are still stored). The Android client must register
its device token via POST /api/devices once google-services.json + firebase-messaging are added.
"""

import json
import logging
import os
from functools import lru_cache
from typing import Any

import requests

from app.core.config import Settings, get_settings
from app.core.db import db

logger = logging.getLogger(__name__)

_FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


@lru_cache
def _load_service_account(path: str) -> dict[str, Any] | None:
    if not path or not os.path.exists(path):
        return None
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception as exc:  # noqa: BLE001
        logger.warning("Could not load FCM service account at %s: %s", path, exc)
        return None


def _access_token(sa: dict[str, Any]) -> str | None:
    try:
        from google.oauth2 import service_account
        from google.auth.transport.requests import Request

        creds = service_account.Credentials.from_service_account_info(sa, scopes=[_FCM_SCOPE])
        creds.refresh(Request())
        return creds.token
    except Exception as exc:  # noqa: BLE001
        logger.warning("FCM token mint failed: %s", exc)
        return None


def _send_one(project_id: str, token: str, device_token: str, title: str, body: str) -> bool:
    resp = requests.post(
        f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json={"message": {"token": device_token, "notification": {"title": title, "body": body}}},
        timeout=30,
    )
    if resp.status_code == 404 or resp.status_code == 400:
        # Stale/invalid token — caller may prune it.
        logger.info("FCM token rejected (HTTP %s)", resp.status_code)
        return False
    resp.raise_for_status()
    return True


async def send_to_user(user_id: str, title: str, body: str, settings: Settings | None = None) -> int:
    """Send a push to all of a user's registered devices. Returns count sent (0 = disabled/none)."""
    settings = settings or get_settings()
    if not settings.fcm_service_account_file:
        return 0
    sa = _load_service_account(settings.fcm_service_account_file)
    if not sa:
        return 0
    token = _access_token(sa)
    project_id = sa.get("project_id")
    if not token or not project_id:
        return 0
    devices = await db.devicetoken.find_many(where={"userId": user_id})
    sent = 0
    for d in devices:
        try:
            if _send_one(project_id, token, d.token, title, body):
                sent += 1
            else:
                await db.devicetoken.delete(where={"token": d.token})
        except Exception as exc:  # noqa: BLE001 - one bad token must not stop the rest
            logger.info("FCM send to one device failed: %s", exc)
    return sent
