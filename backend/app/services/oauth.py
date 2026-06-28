"""Microsoft (Azure AD) + Yahoo OAuth 2.0 / OpenID Connect — authorization-code exchange.

The mobile client opens the provider's hosted sign-in page in a browser and receives an
authorization ``code`` at its redirect URI. It forwards that code here; we exchange it for tokens at
the provider's token endpoint (a server-to-server TLS call authenticated with our confidential
client secret) and read the verified profile. Because the ID token is delivered straight from the
provider over TLS to us — never via the untrusted client — decoding its claims is safe without a
second signature round-trip; we still fall back to the provider's userinfo endpoint when needed.

Returns a normalized dict: ``{"email", "name", "sub", "picture"}``. Raises ValueError on any failure
so the auth route can map it to a clean 4xx.
"""

import base64
import json
from typing import Any

import requests

from app.core.config import Settings

_TIMEOUT = 20


def _decode_jwt_claims(token: str) -> dict[str, Any]:
    """Read (not verify) the payload of a JWT we received directly from the provider's token endpoint."""
    try:
        payload_b64 = token.split(".")[1]
        payload_b64 += "=" * (-len(payload_b64) % 4)  # restore base64 padding
        return json.loads(base64.urlsafe_b64decode(payload_b64))
    except Exception:  # noqa: BLE001
        return {}


def _post_token(url: str, data: dict[str, str]) -> dict[str, Any]:
    resp = requests.post(
        url,
        data=data,
        headers={"Accept": "application/json", "Content-Type": "application/x-www-form-urlencoded"},
        timeout=_TIMEOUT,
    )
    if resp.status_code >= 400:
        raise ValueError(f"Token exchange failed (HTTP {resp.status_code}): {resp.text[:300]}")
    return resp.json()


def exchange_microsoft(code: str, redirect_uri: str, settings: Settings) -> dict[str, Any]:
    if not settings.microsoft_client_id or not settings.microsoft_client_secret:
        raise ValueError("Microsoft sign-in is not configured on the server.")
    tenant = settings.microsoft_tenant or "common"
    tokens = _post_token(
        f"https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token",
        {
            "client_id": settings.microsoft_client_id,
            "client_secret": settings.microsoft_client_secret,
            "code": code,
            "redirect_uri": redirect_uri,
            "grant_type": "authorization_code",
            "scope": "openid email profile User.Read",
        },
    )
    claims = _decode_jwt_claims(tokens.get("id_token", "")) if tokens.get("id_token") else {}
    email = claims.get("email") or claims.get("preferred_username")
    name = claims.get("name")
    sub = claims.get("sub") or claims.get("oid")
    if not email and tokens.get("access_token"):
        # Fall back to Microsoft Graph for the profile.
        me = requests.get(
            "https://graph.microsoft.com/v1.0/me",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
            timeout=_TIMEOUT,
        )
        if me.ok:
            body = me.json()
            email = email or body.get("mail") or body.get("userPrincipalName")
            name = name or body.get("displayName")
            sub = sub or body.get("id")
    if not email:
        raise ValueError("Microsoft did not return an email address.")
    return {"email": str(email).lower(), "name": name, "sub": sub, "picture": None}


def exchange_yahoo(code: str, redirect_uri: str, settings: Settings) -> dict[str, Any]:
    if not settings.yahoo_client_id or not settings.yahoo_client_secret:
        raise ValueError("Yahoo sign-in is not configured on the server.")
    # Yahoo expects HTTP Basic auth (client_id:client_secret) on the token endpoint.
    basic = base64.b64encode(
        f"{settings.yahoo_client_id}:{settings.yahoo_client_secret}".encode()
    ).decode()
    resp = requests.post(
        "https://api.login.yahoo.com/oauth2/get_token",
        data={"grant_type": "authorization_code", "redirect_uri": redirect_uri, "code": code},
        headers={
            "Authorization": f"Basic {basic}",
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        },
        timeout=_TIMEOUT,
    )
    if resp.status_code >= 400:
        raise ValueError(f"Yahoo token exchange failed (HTTP {resp.status_code}): {resp.text[:300]}")
    tokens = resp.json()
    claims = _decode_jwt_claims(tokens.get("id_token", "")) if tokens.get("id_token") else {}
    email = claims.get("email")
    name = claims.get("name") or claims.get("given_name")
    sub = claims.get("sub")
    if not email and tokens.get("access_token"):
        ui = requests.get(
            "https://api.login.yahoo.com/openid/v1/userinfo",
            headers={"Authorization": f"Bearer {tokens['access_token']}"},
            timeout=_TIMEOUT,
        )
        if ui.ok:
            body = ui.json()
            email = email or body.get("email")
            name = name or body.get("name") or body.get("given_name")
            sub = sub or body.get("sub")
    if not email:
        raise ValueError("Yahoo did not return an email address.")
    return {"email": str(email).lower(), "name": name, "sub": sub, "picture": None}


def exchange_code(provider: str, code: str, redirect_uri: str, settings: Settings) -> dict[str, Any]:
    key = (provider or "").upper()
    if key == "MICROSOFT":
        return exchange_microsoft(code, redirect_uri, settings)
    if key == "YAHOO":
        return exchange_yahoo(code, redirect_uri, settings)
    raise ValueError(f"Unsupported OAuth provider: {provider}")
