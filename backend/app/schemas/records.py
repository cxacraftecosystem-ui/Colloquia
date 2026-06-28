from typing import Any

from pydantic import BaseModel


class RecordingUpdate(BaseModel):
    title: str | None = None
    folderId: str | None = None
    isFavorite: bool | None = None
    isArchived: bool | None = None
    isPinned: bool | None = None
    language: str | None = None


class FolderCreate(BaseModel):
    name: str
    color: str | None = None
    parentId: str | None = None


class FolderUpdate(BaseModel):
    name: str | None = None
    color: str | None = None
    parentId: str | None = None


class TagCreate(BaseModel):
    name: str


class TagAssign(BaseModel):
    tagIds: list[str]


class SegmentEdit(BaseModel):
    text: str


class SpeakerRename(BaseModel):
    # Rename a speaker label (e.g. "Speaker 1" -> "Ankit") everywhere it appears for this recording.
    old: str
    new: str


class ManualTranscript(BaseModel):
    rawText: str


class ChatRequest(BaseModel):
    question: str


class TransformRequest(BaseModel):
    # Used by translate / rewrite / tone / custom-prompt. `instruction` is built server-side for the
    # named transforms; `custom` carries a free-form user prompt.
    targetLanguage: str | None = None
    tone: str | None = None
    instruction: str | None = None


class SummaryRegenerate(BaseModel):
    type: str  # BRIEF | DETAILED | BULLETS | MINUTES


class SettingsUpdate(BaseModel):
    transcriptionMode: str | None = None
    defaultLanguage: str | None = None
    batchWindowEnabled: bool | None = None
    batchWindowStart: str | None = None
    batchWindowEnd: str | None = None
    batchTimezone: str | None = None


class UserSettingsUpdate(BaseModel):
    settingsJson: dict[str, Any]
