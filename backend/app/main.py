import asyncio
import logging
import os
import tempfile
from contextlib import asynccontextmanager, suppress
from typing import Any

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router, live_router
from app.core.config import get_settings
from app.core.db import connect_db, db, disconnect_db
from app.services.media_queue import process_next_jobs

logger = logging.getLogger(__name__)

# Host-wide lock electing ONE queue worker across all uvicorn worker processes, so heavy Whisper/AI
# work never runs in every worker at once (which would starve the request path). Ported from the
# field-repo. On platforms without fcntl (Windows dev) the lock is granted unconditionally.
_QUEUE_LOCK_PATH = os.path.join(tempfile.gettempdir(), "transcriptai-queue.lock")


def _acquire_queue_worker_lock() -> Any | None:
    try:
        import fcntl  # POSIX only
    except ImportError:
        return object()
    try:
        handle = open(_QUEUE_LOCK_PATH, "w")  # noqa: SIM115 - held for process lifetime
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        handle.write(str(os.getpid()))
        handle.flush()
        return handle
    except OSError:
        return None


async def _keep_db_connected() -> None:
    delay = 2.0
    while not db.is_connected():
        try:
            await db.connect()
        except Exception as exc:  # noqa: BLE001
            logger.warning("Background DB reconnect failed: %s — retrying in %.0fs", exc, delay)
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30.0)
    logger.info("Database connected (background reconnect succeeded)")


@asynccontextmanager
async def lifespan(app: FastAPI):
    db_reconnect_task: asyncio.Task[None] | None = None
    try:
        await connect_db()
    except Exception as exc:  # noqa: BLE001
        logger.error("Initial DB connect failed (%s); reconnecting in background", exc)
        db_reconnect_task = asyncio.create_task(_keep_db_connected())

    settings = get_settings()
    queue_task: asyncio.Task[None] | None = None
    queue_lock: Any | None = None
    if settings.media_queue_worker_enabled:
        queue_lock = _acquire_queue_worker_lock()
        if queue_lock is not None:
            logger.info("Queue worker elected in pid %s", os.getpid())
            queue_task = asyncio.create_task(_queue_worker())
        else:
            logger.info("Queue worker already running elsewhere; pid %s serves requests only", os.getpid())
    try:
        yield
    finally:
        if db_reconnect_task:
            db_reconnect_task.cancel()
            with suppress(asyncio.CancelledError):
                await db_reconnect_task
        if queue_task:
            queue_task.cancel()
            with suppress(asyncio.CancelledError):
                await queue_task
        if queue_lock is not None and hasattr(queue_lock, "close"):
            with suppress(Exception):
                queue_lock.close()
        await disconnect_db()


async def _queue_worker() -> None:
    settings = get_settings()
    interval = max(settings.media_queue_interval_seconds, 1.0)
    while True:
        try:
            await process_next_jobs(
                limit=settings.media_queue_batch_size,
                worker_id="fastapi-background",
                settings=settings,
            )
        except Exception:
            logger.exception("Queue worker iteration failed")
        await asyncio.sleep(interval)


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Colloquia API",
        version="0.2.0",
        description="Colloquia — audio recording + transcription + AI analysis backend.",
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins or ["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/health", tags=["health"])
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ai", tags=["health"])
    async def health_ai() -> dict[str, Any]:
        """Whether the configured OpenAI key can actually be used right now (sanitized; never the key).
        `available: false` with errorCode `insufficient_quota` => the OpenAI account is out of credits."""
        from app.core.config import get_settings
        from app.services.ai import ai_healthcheck

        return await ai_healthcheck(get_settings())

    app.include_router(api_router)
    app.include_router(live_router)  # WebSocket: /api/live/transcribe
    return app


app = create_app()
