import logging
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token

from app.core.config import get_settings
from app.core.db import db
from app.core.deps import get_current_user
from app.core.security import create_access_token, verify_password
from app.schemas.auth import LoginRequest, ProfileUpdate, TokenResponse
from app.services import oauth as oauth_service

router = APIRouter(prefix="/auth", tags=["auth"])
logger = logging.getLogger(__name__)


def serialize_user(user: Any) -> dict[str, Any]:
    payload = jsonable_encoder(user)
    payload.pop("passwordHash", None)
    return payload


def enum_value(value: Any) -> str:
    return str(getattr(value, "value", value))


def role_for_email(email: str) -> str:
    settings = get_settings()
    if settings.master_admin_email and email.lower() == settings.master_admin_email.lower():
        return "MASTER_ADMIN"
    return "USER"


def verify_google_token(token: str) -> dict[str, Any]:
    settings = get_settings()
    if not settings.google_client_ids:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Google OAuth is not configured")
    last_error: ValueError | None = None
    for client_id in settings.google_client_ids:
        try:
            return google_id_token.verify_oauth2_token(token, google_requests.Request(), client_id)
        except ValueError as exc:
            last_error = exc
            logger.info("Google token rejected for audience %s: %s", client_id, exc)
    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Google ID token") from last_error


async def login_with_google(token: str) -> Any:
    id_info = verify_google_token(token)
    if not id_info.get("email") or not id_info.get("email_verified"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Google email not verified")
    email = id_info["email"].lower()
    settings = get_settings()
    role = role_for_email(email)
    name = settings.master_admin_name if role == "MASTER_ADMIN" else (id_info.get("name") or email.split("@")[0])
    avatar_url = id_info.get("picture")

    existing = await db.user.find_unique(where={"email": email})
    if existing:
        data: dict[str, Any] = {"name": name, "avatarUrl": avatar_url, "authProvider": "GOOGLE"}
        if role == "MASTER_ADMIN":
            data["role"] = "MASTER_ADMIN"
        return await db.user.update(where={"email": email}, data=data)
    return await db.user.create(
        data={"email": email, "name": name, "avatarUrl": avatar_url, "authProvider": "GOOGLE", "role": role}
    )


async def login_with_oauth_code(provider: str, code: str, redirect_uri: str | None) -> Any:
    """Microsoft / Yahoo authorization-code sign-in: exchange the code, then upsert the user."""
    settings = get_settings()
    if not redirect_uri:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Missing redirectUri")
    try:
        profile = oauth_service.exchange_code(provider, code, redirect_uri, settings)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    email = profile["email"]
    auth_provider = provider.upper()
    role = role_for_email(email)
    name = settings.master_admin_name if role == "MASTER_ADMIN" else (profile.get("name") or email.split("@")[0])
    existing = await db.user.find_unique(where={"email": email})
    if existing:
        data: dict[str, Any] = {"name": name, "authProvider": auth_provider}
        if profile.get("picture"):
            data["avatarUrl"] = profile["picture"]
        if role == "MASTER_ADMIN":
            data["role"] = "MASTER_ADMIN"
        return await db.user.update(where={"email": email}, data=data)
    return await db.user.create(
        data={"email": email, "name": name, "avatarUrl": profile.get("picture"), "authProvider": auth_provider, "role": role}
    )


@router.post("/login", response_model=TokenResponse)
async def login(payload: LoginRequest) -> dict[str, Any]:
    if payload.googleIdToken:
        user = await login_with_google(payload.googleIdToken)
    elif payload.provider and payload.code:
        user = await login_with_oauth_code(payload.provider, payload.code, payload.redirectUri)
    else:
        user = await db.user.find_unique(where={"email": (payload.email or "").lower()})
        if not user or not verify_password(payload.password or "", user.passwordHash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password")

    access_token = create_access_token(
        subject=user.id, extra_claims={"email": user.email, "role": enum_value(user.role)}
    )
    return {"accessToken": access_token, "tokenType": "bearer", "user": serialize_user(user)}


@router.post("/google", response_model=TokenResponse)
async def google_login(payload: LoginRequest) -> dict[str, Any]:
    if not payload.googleIdToken:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Missing Google ID token")
    return await login(payload)


@router.post("/logout")
async def logout() -> dict[str, bool]:
    return {"ok": True}


@router.get("/me")
async def me(current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    return serialize_user(current_user)


@router.patch("/me")
async def update_me(payload: ProfileUpdate, current_user: Any = Depends(get_current_user)) -> dict[str, Any]:
    data = {k: v for k, v in payload.model_dump(exclude_unset=True).items() if v is not None}
    if "settingsJson" in data:
        from prisma import Json

        data["settingsJson"] = Json(data["settingsJson"])
    if not data:
        return serialize_user(current_user)
    user = await db.user.update(where={"id": current_user.id}, data=data)
    return serialize_user(user)
