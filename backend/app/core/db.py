import asyncio
import logging
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from prisma import Prisma

from app.core.config import get_settings

logger = logging.getLogger(__name__)

_POOLER_HOST_SUFFIX = ".pooler.supabase.com"


def build_runtime_database_url(base_url: str) -> str:
    """Route the runtime Prisma client through the Supabase TRANSACTION-mode pooler (:6543,
    pgbouncer=true) instead of the session pooler (:5432). Session mode pins one of only 15 server
    connections per client for its whole life, so a couple of uvicorn workers exhaust the pool;
    transaction mode releases the connection per statement and multiplexes many app connections.
    Migrations still use the session URL from the env. Non-pooler hosts are returned unchanged.
    Ported from the field-repository backend.
    """
    settings = get_settings()
    if not settings.database_use_transaction_pooler:
        return base_url
    try:
        parts = urlsplit(base_url)
    except ValueError:
        return base_url
    host = parts.hostname or ""
    if not host.endswith(_POOLER_HOST_SUFFIX):
        return base_url

    userinfo = ""
    if parts.username:
        userinfo = parts.username
        if parts.password:
            userinfo += f":{parts.password}"
        userinfo += "@"
    netloc = f"{userinfo}{host}:6543"

    query = dict(parse_qsl(parts.query, keep_blank_values=True))
    query["pgbouncer"] = "true"
    query["connection_limit"] = str(settings.database_connection_limit)
    if settings.database_pool_timeout is not None:
        query["pool_timeout"] = str(settings.database_pool_timeout)

    return urlunsplit((parts.scheme, netloc, parts.path, urlencode(query), parts.fragment))


db = Prisma(datasource={"url": build_runtime_database_url(get_settings().database_url)})


async def connect_db() -> None:
    """Connect the runtime client, retrying a transient pooler-full / engine-connect failure with
    backoff so a momentary spike doesn't crash-loop the service."""
    if db.is_connected():
        return
    attempts = 6
    delay = 2.0
    for attempt in range(1, attempts + 1):
        try:
            await db.connect()
            return
        except Exception as exc:  # noqa: BLE001
            if attempt == attempts:
                raise
            logger.warning(
                "Database connect failed (attempt %s/%s): %s — retrying in %.0fs",
                attempt, attempts, exc, delay,
            )
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30.0)


async def disconnect_db() -> None:
    if db.is_connected():
        await db.disconnect()
