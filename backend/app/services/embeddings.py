"""Semantic search + knowledge retrieval over transcript segment embeddings.

Vectors are stored as JSON float arrays in the Embedding table; similarity is cosine computed in app
code (no pgvector dependency — fine for personal-scale corpora). Generation is gated by lite mode:
when lite, we skip indexing and callers fall back to keyword search.
"""

import math
from typing import Any

from app.core.config import Settings
from app.core.db import db
from app.services import ai


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


async def index_recording(recording_id: str, user_id: str, settings: Settings) -> int:
    """Embed a recording's segments (or raw text) and store vectors. Returns count indexed (0 in lite
    mode or when no API key)."""
    if settings.lite_mode or not settings.openai_api_key:
        return 0
    transcript = await db.transcript.find_unique(where={"recordingId": recording_id})
    if not transcript:
        return 0
    segments = await db.transcriptsegment.find_many(where={"transcriptId": transcript.id}, order={"idx": "asc"})
    units: list[tuple[int | None, str]] = []
    if segments:
        # Group ~3 segments per embedding unit to keep vectors meaningful and counts modest.
        buf: list[Any] = []
        for s in segments:
            buf.append(s)
            if len(buf) >= 3:
                units.append((buf[0].idx, " ".join((x.editedText or x.text) for x in buf)))
                buf = []
        if buf:
            units.append((buf[0].idx, " ".join((x.editedText or x.text) for x in buf)))
    elif transcript.rawText:
        units.append((None, transcript.rawText[:4000]))
    if not units:
        return 0

    vectors = await ai.embed_texts([u[1] for u in units], settings)
    if not vectors:
        return 0
    from prisma import Json

    await db.embedding.delete_many(where={"recordingId": recording_id})
    for (idx, text), vec in zip(units, vectors):
        await db.embedding.create(
            data={"userId": user_id, "recordingId": recording_id, "segmentIdx": idx, "text": text, "vector": Json(vec)}
        )
    return len(units)


async def semantic_search(user_id: str, query: str, settings: Settings, limit: int = 10) -> list[dict[str, Any]]:
    """Top-k semantically similar snippets across the user's recordings. Empty list when no index/key."""
    qvec = await ai.embed_query(query, settings)
    if not qvec:
        return []
    rows = await db.embedding.find_many(where={"userId": user_id}, take=2000)
    scored: list[tuple[float, Any]] = []
    for row in rows:
        vec = row.vector if isinstance(row.vector, list) else []
        scored.append((_cosine(qvec, vec), row))
    scored.sort(key=lambda t: t[0], reverse=True)
    return [
        {"recordingId": r.recordingId, "segmentIdx": r.segmentIdx, "text": r.text, "score": round(score, 4)}
        for score, r in scored[:limit]
        if score > 0
    ]
