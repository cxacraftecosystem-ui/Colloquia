from typing import Any

from pydantic import BaseModel


class IntegrationUpsert(BaseModel):
    provider: str
    config: dict[str, Any] | None = None
    enabled: bool = True


class PushRequest(BaseModel):
    provider: str


class WorkspaceCreate(BaseModel):
    name: str


class MemberAdd(BaseModel):
    userId: str
    role: str = "VIEWER"


class ShareCreate(BaseModel):
    userId: str
    tier: str = "VIEW"


class TemplateCreate(BaseModel):
    name: str
    prompts: list[dict[str, str]]


class RuleUpsert(BaseModel):
    name: str
    trigger: str = "ON_ANALYZED"
    action: str
    config: dict[str, Any] | None = None
    enabled: bool = True


class GoogleActionRequest(BaseModel):
    googleAccessToken: str | None = None
    # For email drafts.
    to: str | None = None


class KnowledgeQuery(BaseModel):
    query: str


class VoiceCommand(BaseModel):
    command: str


class DeviceRegister(BaseModel):
    token: str
    platform: str = "android"
