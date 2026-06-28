from typing import Any

from fastapi import APIRouter, Depends

from app.core.config import get_settings
from app.core.deps import get_current_user
from app.schemas.phase2 import VoiceCommand
from app.services import ai as ai_service

router = APIRouter(prefix="/voice", tags=["voice"])


@router.post("/command")
async def voice_command(payload: VoiceCommand, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Map a spoken/typed command to a structured intent the client can act on (start/stop recording,
    search, summarize, open latest, create task)."""
    return await ai_service.voice_command_intent(payload.command, get_settings())
