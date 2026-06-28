"""Live (near-real-time) transcription over a WebSocket.

The client streams audio chunks (binary frames, each a self-contained short clip e.g. webm/m4a/wav);
the server transcribes each chunk with Whisper and streams back incremental text. This is the
compute-heavy path: when LITE_MODE is on the socket politely refuses with a notice so a free-tier box
isn't overwhelmed (clients then use ordinary record→upload→transcribe).

Auth: pass the JWT as the `token` query param (WebSocket can't send Authorization headers easily).
"""

import logging
from typing import Any

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from app.core.config import get_settings
from app.core.db import db
from app.core.security import decode_access_token
from app.services.ai import transcribe_audio_bytes

router = APIRouter(tags=["live"])
logger = logging.getLogger(__name__)


async def _user_from_token(token: str | None) -> Any | None:
    if not token:
        return None
    try:
        payload = decode_access_token(token)
    except ValueError:
        return None
    uid = payload.get("sub")
    if not uid:
        return None
    return await db.user.find_unique(where={"id": uid})


@router.websocket("/api/live/transcribe")
async def live_transcribe(websocket: WebSocket, token: str | None = Query(default=None)) -> None:
    await websocket.accept()
    settings = get_settings()

    user = await _user_from_token(token)
    if user is None:
        await websocket.send_json({"type": "error", "message": "unauthorized"})
        await websocket.close()
        return

    if settings.lite_mode:
        await websocket.send_json({
            "type": "unavailable",
            "message": "Live transcription is disabled in lite mode. Record and upload instead.",
        })
        await websocket.close()
        return

    await websocket.send_json({"type": "ready"})
    chunk_index = 0
    try:
        while True:
            message = await websocket.receive()
            data = message.get("bytes")
            if data is None:
                # A text frame: allow a {"type":"stop"} control message.
                if message.get("text") == "stop":
                    break
                continue
            chunk_index += 1
            result = await transcribe_audio_bytes(data, f"live-{chunk_index}.webm", "audio/webm", settings)
            await websocket.send_json({
                "type": "partial",
                "index": chunk_index,
                "status": result.get("status"),
                "text": result.get("text") or "",
            })
    except WebSocketDisconnect:
        pass
    except Exception as exc:  # noqa: BLE001
        logger.info("Live transcribe error: %s", exc)
        try:
            await websocket.send_json({"type": "error", "message": str(exc)})
        except Exception:  # noqa: BLE001
            pass
    finally:
        try:
            await websocket.send_json({"type": "done"})
            await websocket.close()
        except Exception:  # noqa: BLE001
            pass
