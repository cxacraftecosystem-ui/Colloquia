from typing import Any

from pydantic import BaseModel, EmailStr


class LoginRequest(BaseModel):
    email: EmailStr | None = None
    password: str | None = None
    googleIdToken: str | None = None
    # Generic OAuth authorization-code sign-in (Microsoft / Yahoo): provider + code from the client's
    # redirect, plus the exact redirectUri used (the provider checks it during exchange).
    provider: str | None = None
    code: str | None = None
    redirectUri: str | None = None


class TokenResponse(BaseModel):
    accessToken: str
    tokenType: str = "bearer"
    user: dict[str, Any]


class ProfileUpdate(BaseModel):
    name: str | None = None
    avatarUrl: str | None = None
    settingsJson: dict[str, Any] | None = None
