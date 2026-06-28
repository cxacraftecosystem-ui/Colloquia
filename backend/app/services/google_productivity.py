"""Google Calendar / Tasks / Gmail actions.

The mobile client obtains a Google OAuth ACCESS token with the needed scopes (calendar.events,
tasks, gmail.compose) and passes it per request; the backend relays to the Google REST APIs. This
keeps refresh-token storage out of the backend for phase 1. Absent token -> NEEDS_AUTH.
"""

import base64
from email.mime.text import MIMEText
from typing import Any

import requests


def _auth(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def create_calendar_event(access_token: str, summary: str, description: str, start_iso: str, end_iso: str) -> dict[str, Any]:
    if not access_token:
        return {"status": "NEEDS_AUTH"}
    r = requests.post(
        "https://www.googleapis.com/calendar/v3/calendars/primary/events",
        headers=_auth(access_token),
        json={
            "summary": summary,
            "description": description,
            "start": {"dateTime": start_iso},
            "end": {"dateTime": end_iso},
        },
        timeout=30,
    )
    if r.status_code >= 400:
        return {"status": "FAILED", "message": r.text[:300]}
    return {"status": "CREATED", "id": r.json().get("id"), "htmlLink": r.json().get("htmlLink")}


def create_task(access_token: str, title: str, notes: str, due_iso: str | None) -> dict[str, Any]:
    if not access_token:
        return {"status": "NEEDS_AUTH"}
    body: dict[str, Any] = {"title": title, "notes": notes}
    if due_iso:
        body["due"] = due_iso
    r = requests.post(
        "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks",
        headers=_auth(access_token),
        json=body,
        timeout=30,
    )
    if r.status_code >= 400:
        return {"status": "FAILED", "message": r.text[:300]}
    return {"status": "CREATED", "id": r.json().get("id")}


def create_gmail_draft(access_token: str, to: str, subject: str, body: str) -> dict[str, Any]:
    if not access_token:
        return {"status": "NEEDS_AUTH"}
    mime = MIMEText(body)
    mime["to"] = to
    mime["subject"] = subject
    raw = base64.urlsafe_b64encode(mime.as_bytes()).decode()
    r = requests.post(
        "https://gmail.googleapis.com/gmail/v1/users/me/drafts",
        headers=_auth(access_token),
        json={"message": {"raw": raw}},
        timeout=30,
    )
    if r.status_code >= 400:
        return {"status": "FAILED", "message": r.text[:300]}
    return {"status": "CREATED", "id": r.json().get("id")}
