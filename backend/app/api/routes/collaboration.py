from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.phase2 import MemberAdd, ShareCreate, WorkspaceCreate

router = APIRouter(prefix="/collab", tags=["collaboration"])


@router.get("/workspaces")
async def list_workspaces(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    member_rows = await db.workspacemember.find_many(where={"userId": current_user.id}, include={"workspace": True})
    owned = await db.workspace.find_many(where={"ownerId": current_user.id})
    seen = {w.id: w for w in owned}
    for m in member_rows:
        if m.workspace:
            seen[m.workspace.id] = m.workspace
    return jsonable_encoder(list(seen.values()))


@router.post("/workspaces", status_code=status.HTTP_201_CREATED)
async def create_workspace(payload: WorkspaceCreate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    ws = await db.workspace.create(data={"name": payload.name, "ownerId": current_user.id})
    await db.workspacemember.create(data={"workspaceId": ws.id, "userId": current_user.id, "role": "OWNER"})
    return jsonable_encoder(ws)


@router.post("/workspaces/{ws_id}/members")
async def add_member(ws_id: str, payload: MemberAdd, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    ws = await db.workspace.find_unique(where={"id": ws_id})
    if not ws:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Workspace not found")
    if ws.ownerId != current_user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the owner can add members")
    member = await db.workspacemember.upsert(
        where={"workspaceId_userId": {"workspaceId": ws_id, "userId": payload.userId}},
        data={
            "create": {"workspaceId": ws_id, "userId": payload.userId, "role": payload.role},
            "update": {"role": payload.role},
        },
    )
    return jsonable_encoder(member)


@router.post("/recordings/{recording_id}/share")
async def share_recording(recording_id: str, payload: ShareCreate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recording not found")
    assert_owns(rec, current_user)
    share = await db.recordingshare.upsert(
        where={"recordingId_userId": {"recordingId": recording_id, "userId": payload.userId}},
        data={
            "create": {"recordingId": recording_id, "ownerId": current_user.id, "userId": payload.userId, "tier": payload.tier},
            "update": {"tier": payload.tier},
        },
    )
    return jsonable_encoder(share)


@router.get("/shared-with-me")
async def shared_with_me(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    shares = await db.recordingshare.find_many(where={"userId": current_user.id})
    rec_ids = [s.recordingId for s in shares]
    if not rec_ids:
        return []
    recs = await db.recording.find_many(where={"id": {"in": rec_ids}})
    tier_by_id = {s.recordingId: s.tier for s in shares}
    out = []
    for r in recs:
        d = jsonable_encoder(r)
        d["sharedTier"] = tier_by_id.get(r.id)
        out.append(d)
    return out
