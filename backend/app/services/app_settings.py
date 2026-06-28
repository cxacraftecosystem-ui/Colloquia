"""Global, master-admin-configurable settings (a single DB row).

Controls how transcripts are produced (RAW / REFINED / REFINED_TRANSLATED) and an optional off-peak
window during which heavy transcription + analysis runs. Ported from the field-repository backend.
"""

from datetime import datetime, time
from typing import Any
from zoneinfo import ZoneInfo

from app.core.db import db

SINGLETON_ID = "singleton"
# Default: clean up the dialogue AND translate it to English when the source isn't English. The
# untranslated refined version is kept too (see Transcript.refinedText vs translatedText).
DEFAULT_TRANSCRIPTION_MODE = "REFINED_TRANSLATED"
VALID_TRANSCRIPTION_MODES = {"RAW", "REFINED", "REFINED_TRANSLATED"}
DEFAULT_TIMEZONE = "Asia/Kolkata"


async def load_app_settings() -> Any | None:
    try:
        return await db.appsetting.find_unique(where={"id": SINGLETON_ID})
    except Exception:  # noqa: BLE001 - settings must never break the request/worker path
        return None


async def get_or_create_app_settings(updated_by_id: str | None = None) -> Any:
    existing = await load_app_settings()
    if existing is not None:
        return existing
    return await db.appsetting.create(data={"id": SINGLETON_ID, "updatedById": updated_by_id})


def transcription_mode(row: Any | None) -> str:
    if row is None:
        return DEFAULT_TRANSCRIPTION_MODE
    mode = getattr(row, "transcriptionMode", None)
    return mode if mode in VALID_TRANSCRIPTION_MODES else DEFAULT_TRANSCRIPTION_MODE


def parse_hhmm(value: str | None, fallback: time) -> time:
    if not value:
        return fallback
    try:
        hours, minutes = str(value).split(":")
        return time(int(hours) % 24, int(minutes) % 60)
    except (ValueError, AttributeError):
        return fallback


def is_valid_hhmm(value: str | None) -> bool:
    if not value:
        return False
    try:
        hours, minutes = str(value).split(":")
        return 0 <= int(hours) <= 23 and 0 <= int(minutes) <= 59
    except (ValueError, AttributeError):
        return False


def within_processing_window(row: Any | None, now: datetime | None = None) -> bool:
    """True when heavy transcription/analysis jobs may run right now. Always true when the off-peak
    window is disabled; otherwise true only inside [start, end) in the configured timezone, handling a
    window that wraps past midnight."""
    if row is None or not getattr(row, "batchWindowEnabled", False):
        return True
    tz_name = getattr(row, "batchTimezone", None) or DEFAULT_TIMEZONE
    try:
        tz = ZoneInfo(tz_name)
    except Exception:  # noqa: BLE001
        tz = ZoneInfo(DEFAULT_TIMEZONE)
    current = (now or datetime.now(tz)).astimezone(tz).time()
    start = parse_hhmm(getattr(row, "batchWindowStart", None), time(2, 0))
    end = parse_hhmm(getattr(row, "batchWindowEnd", None), time(5, 0))
    if start == end:
        return True
    if start < end:
        return start <= current < end
    return current >= start or current < end
