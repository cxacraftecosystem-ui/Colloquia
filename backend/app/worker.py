"""Standalone media-processing queue worker for Colloquia.

Runs the transcription/analysis queue in its OWN process (systemd unit ``colloquia-queue.service``),
completely separate from the web (uvicorn) process. This split fixes a production-class outage and MUST
stay separate:

1. **No multiprocess supervisor to SIGKILL it.** The web service runs as a SINGLE uvicorn process.
   With ``--workers >1`` uvicorn runs a multiprocess supervisor that health-pings each worker over a
   pipe and SIGKILLs any worker that fails to pong within ``timeout_worker_healthcheck``. On a small,
   CPU-credit-throttled EC2 box a heavy transcription chunk (run via ``asyncio.to_thread``) starves
   that pong thread, so the supervisor kills the worker mid-job. A SIGKILLed process never runs its
   shutdown hook, so its Prisma query-engine subprocess is orphaned (reparented to init). One orphan
   per kill cycle eventually exhausts the Supabase pooler's client-connection ceiling, after which
   EVERY DB call (login included) returns HTTP 500 while ``/health`` (no DB) keeps returning 200.

2. **Request latency isolation.** Transcription/analysis read whole media files into memory and run
   ffmpeg + AI calls. Keeping that off the request-serving process means API responses stay fast and
   never trip CloudFront's origin-response timeout (the HTTP 504 class of failure).

A plain ``asyncio.run`` loop — no supervisor, no health-ping, nothing that can kill it mid-flight.
systemd restarts it (``Restart=always``) only if it actually exits, and ``KillMode=control-group``
reaps its query-engine on stop/restart so it is never orphaned.
"""

from __future__ import annotations

import asyncio
import logging
import signal
from contextlib import suppress

from app.core.config import get_settings
from app.core.db import connect_db, db, disconnect_db
from app.services.media_queue import process_next_jobs

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("app.worker")


async def _run() -> None:
    settings = get_settings()
    interval = max(settings.media_queue_interval_seconds, 1.0)

    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        with suppress(NotImplementedError):  # add_signal_handler is POSIX-only
            loop.add_signal_handler(sig, stop.set)

    # Connect with the same resilient retry the web app uses. If it ultimately fails we do NOT crash:
    # the loop below keeps retrying, so a momentarily-full pooler can drain without a restart storm.
    with suppress(Exception):
        await connect_db()

    logger.info("Colloquia media queue worker started (interval=%.1fs)", interval)
    try:
        while not stop.is_set():
            try:
                if not db.is_connected():
                    await connect_db()
                await process_next_jobs(
                    limit=settings.media_queue_batch_size,
                    worker_id="queue-service",
                    settings=settings,
                )
            except Exception:  # noqa: BLE001 - one bad iteration must never kill the worker
                logger.exception("Media queue iteration failed; backing off")
            with suppress(asyncio.TimeoutError):
                await asyncio.wait_for(stop.wait(), timeout=interval)
    finally:
        await disconnect_db()
        logger.info("Colloquia media queue worker stopped")


def main() -> None:
    asyncio.run(_run())


if __name__ == "__main__":
    main()
