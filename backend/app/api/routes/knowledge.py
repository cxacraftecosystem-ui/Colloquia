from collections import Counter
from typing import Any

from fastapi import APIRouter, Depends, Query

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import get_current_user
from app.schemas.phase2 import KnowledgeQuery
from app.services import ai as ai_service
from app.services import embeddings as emb

router = APIRouter(prefix="/knowledge", tags=["knowledge"])


@router.get("/search")
async def semantic_search(q: str, current_user: Any = Depends(get_current_user), limit: int = Query(10, ge=1, le=50)) -> dict[str, Any]:
    """Semantic search across the user's recordings. Falls back to keyword search in lite mode or when
    embeddings aren't available."""
    settings = get_settings()
    hits = await emb.semantic_search(current_user.id, q, settings, limit)
    if hits:
        return {"mode": "semantic", "results": hits}
    # Keyword fallback.
    segs = await db.transcriptsegment.find_many(
        where={
            "transcript": {"recording": {"userId": current_user.id}},
            "OR": [{"text": {"contains": q, "mode": "insensitive"}}, {"editedText": {"contains": q, "mode": "insensitive"}}],
        },
        take=limit,
    )
    return {
        "mode": "keyword",
        "results": [{"recordingId": None, "segmentIdx": s.idx, "text": s.editedText or s.text, "score": None} for s in segs],
    }


@router.post("/ask")
async def cross_transcript_ask(payload: KnowledgeQuery, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Cross-transcript Q&A: retrieve the most relevant snippets, then answer over them."""
    settings = get_settings()
    hits = await emb.semantic_search(current_user.id, payload.query, settings, 8)
    if not hits:
        # Fall back to most-recent transcripts as context.
        recs = await db.recording.find_many(where={"userId": current_user.id, "transcriptStatus": "COMPLETED"}, order={"createdAt": "desc"}, take=3)
        blob_parts = []
        for r in recs:
            t = await db.transcript.find_unique(where={"recordingId": r.id})
            if t and (t.refinedText or t.rawText):
                blob_parts.append(f"[{r.title}] " + (t.refinedText or t.rawText or "")[:4000])
        blob = "\n\n".join(blob_parts)
    else:
        blob = "\n\n".join(f"- {h['text']}" for h in hits)
    return await ai_service.answer_across(blob, payload.query, settings)


@router.get("/graph")
async def knowledge_graph(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    """Personal knowledge graph: top entities/topics and which recordings mention them."""
    recs = await db.recording.find_many(where={"userId": current_user.id})
    rec_ids = [r.id for r in recs]
    title_by_id = {r.id: (r.aiTitle or r.title) for r in recs}
    if not rec_ids:
        return {"nodes": [], "edges": []}
    items = await db.extracteditem.find_many(where={"recordingId": {"in": rec_ids}})
    by_value: dict[str, set[str]] = {}
    counts: Counter = Counter()
    for it in items:
        key = f"{it.kind}:{it.value}"
        by_value.setdefault(key, set()).add(it.recordingId)
        counts[key] += 1
    nodes = [
        {"id": k, "label": k.split(":", 1)[1], "kind": k.split(":", 1)[0], "weight": c}
        for k, c in counts.most_common(60)
    ]
    edges = []
    for k, rids in by_value.items():
        if k not in {n["id"] for n in nodes}:
            continue
        for rid in rids:
            edges.append({"from": k, "to": title_by_id.get(rid, rid)})
    return {"nodes": nodes, "edges": edges[:300]}
