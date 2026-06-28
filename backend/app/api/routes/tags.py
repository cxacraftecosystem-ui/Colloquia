from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.records import TagCreate

router = APIRouter(prefix="/tags", tags=["tags"])


def _serialize(value: Any) -> Any:
    return jsonable_encoder(value)


@router.get("")
async def list_tags(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    tags = await db.tag.find_many(where={"userId": current_user.id}, order={"name": "asc"})
    return _serialize(tags)


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_tag(payload: TagCreate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    name = payload.name.strip()
    if not name:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Tag name required")
    existing = await db.tag.find_unique(where={"userId_name": {"userId": current_user.id, "name": name}})
    if existing:
        return _serialize(existing)
    tag = await db.tag.create(data={"userId": current_user.id, "name": name})
    return _serialize(tag)


@router.delete("/{tag_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_tag(tag_id: str, current_user: Any = Depends(get_current_user)) -> None:
    tag = await db.tag.find_unique(where={"id": tag_id})
    if not tag:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Tag not found")
    assert_owns(tag, current_user)
    await db.tag.delete(where={"id": tag_id})
