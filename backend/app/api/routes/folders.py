from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder

from app.core.db import db
from app.core.deps import assert_owns, get_current_user
from app.schemas.records import FolderCreate, FolderUpdate

router = APIRouter(prefix="/folders", tags=["folders"])


def _serialize(value: Any) -> Any:
    return jsonable_encoder(value)


@router.get("")
async def list_folders(current_user: Any = Depends(get_current_user)) -> list[dict[str, Any]]:
    folders = await db.folder.find_many(
        where={"userId": current_user.id},
        include={"_count": {"select": {"recordings": True}}},
        order={"name": "asc"},
    )
    return _serialize(folders)


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_folder(payload: FolderCreate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    folder = await db.folder.create(
        data={
            "userId": current_user.id,
            "name": payload.name,
            "color": payload.color,
            "parentId": payload.parentId,
        }
    )
    return _serialize(folder)


@router.patch("/{folder_id}")
async def update_folder(folder_id: str, payload: FolderUpdate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    folder = await db.folder.find_unique(where={"id": folder_id})
    if not folder:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Folder not found")
    assert_owns(folder, current_user)
    data = {k: v for k, v in payload.model_dump(exclude_unset=True).items()}
    updated = await db.folder.update(where={"id": folder_id}, data=data)
    return _serialize(updated)


@router.delete("/{folder_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_folder(folder_id: str, current_user: Any = Depends(get_current_user)) -> None:
    folder = await db.folder.find_unique(where={"id": folder_id})
    if not folder:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Folder not found")
    assert_owns(folder, current_user)
    # Recordings keep existing; their folderId is set null by the schema's onDelete: SetNull.
    await db.folder.delete(where={"id": folder_id})
