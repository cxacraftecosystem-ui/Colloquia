from typing import Any

from pydantic import BaseModel, EmailStr


class LoginRequest(BaseModel):
    email: EmailStr | None = None
    password: str | None = None
    googleIdToken: str | None = None


class TokenResponse(BaseModel):
    accessToken: str
    tokenType: str = "bearer"
    user: dict[str, Any]


class ProfileUpdate(BaseModel):
    name: str | None = None
    avatarUrl: str | None = None
    settingsJson: dict[str, Any] | None = None
