from fastapi import APIRouter

from app.api.routes import (
    ai,
    analytics,
    auth,
    automation,
    collaboration,
    devices,
    export,
    folders,
    insights,
    integrations,
    knowledge,
    live,
    media,
    productivity,
    recordings,
    search,
    settings,
    tags,
    templates,
    transcripts,
    voice,
)

api_router = APIRouter(prefix="/api")
# Core
api_router.include_router(auth.router)
api_router.include_router(media.router)
api_router.include_router(recordings.router)
api_router.include_router(transcripts.router)
api_router.include_router(ai.router)
api_router.include_router(export.router)
api_router.include_router(folders.router)
api_router.include_router(tags.router)
api_router.include_router(search.router)
api_router.include_router(analytics.router)
api_router.include_router(settings.router)
# Phase 2
api_router.include_router(integrations.router)
api_router.include_router(productivity.router)
api_router.include_router(collaboration.router)
api_router.include_router(templates.router)
api_router.include_router(automation.router)
api_router.include_router(knowledge.router)
api_router.include_router(insights.router)
api_router.include_router(voice.router)
api_router.include_router(devices.router)

# WebSocket route (registered on the app, not under the /api APIRouter prefix include).
live_router = live.router
