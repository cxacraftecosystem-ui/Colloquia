"""Outbound 3rd-party integration connectors.

Each provider receives a payload (title + summary + action items) and pushes it to the destination.
Slack and Teams use incoming webhooks (no OAuth dance). Notion/Trello/Jira use API tokens stored in the
Integration.config. Everything is best-effort and returns a status dict; a provider that needs
credentials the user hasn't supplied returns status NEEDS_CONFIG instead of raising.
"""

import logging
from typing import Any

import requests

logger = logging.getLogger(__name__)

SUPPORTED_PROVIDERS = {
    "SLACK", "NOTION", "TRELLO", "JIRA", "OUTLOOK", "TEAMS", "ZOOM", "GOOGLE_MEET", "GOOGLE", "WEBHOOK",
}


def _cfg(config: Any, key: str) -> str | None:
    if isinstance(config, dict):
        v = config.get(key)
        return str(v) if v else None
    return None


def push_to_provider(provider: str, config: Any, payload: dict[str, Any]) -> dict[str, Any]:
    provider = provider.upper()
    title = payload.get("title") or "Recording"
    summary = payload.get("summary") or ""
    actions = payload.get("actionItems") or []
    actions_md = "\n".join(f"- {a}" for a in actions)
    text = f"*{title}*\n{summary}" + (f"\n\n*Action items:*\n{actions_md}" if actions_md else "")

    try:
        if provider in {"SLACK", "TEAMS", "OUTLOOK", "WEBHOOK"}:
            url = _cfg(config, "webhookUrl")
            if not url:
                return {"status": "NEEDS_CONFIG", "message": f"{provider} needs a webhookUrl"}
            body = {"text": text} if provider in {"SLACK", "WEBHOOK"} else {"text": text}
            r = requests.post(url, json=body, timeout=30)
            r.raise_for_status()
            return {"status": "SENT"}

        if provider == "NOTION":
            token = _cfg(config, "token")
            database_id = _cfg(config, "databaseId")
            if not token or not database_id:
                return {"status": "NEEDS_CONFIG", "message": "Notion needs token + databaseId"}
            r = requests.post(
                "https://api.notion.com/v1/pages",
                headers={"Authorization": f"Bearer {token}", "Notion-Version": "2022-06-28", "Content-Type": "application/json"},
                json={
                    "parent": {"database_id": database_id},
                    "properties": {"Name": {"title": [{"text": {"content": title}}]}},
                    "children": [{"object": "block", "type": "paragraph", "paragraph": {"rich_text": [{"text": {"content": (summary + "\n" + actions_md)[:1900]}}]}}],
                },
                timeout=30,
            )
            r.raise_for_status()
            return {"status": "SENT"}

        if provider == "TRELLO":
            key = _cfg(config, "key"); token = _cfg(config, "token"); id_list = _cfg(config, "idList")
            if not all([key, token, id_list]):
                return {"status": "NEEDS_CONFIG", "message": "Trello needs key + token + idList"}
            r = requests.post(
                "https://api.trello.com/1/cards",
                params={"key": key, "token": token, "idList": id_list, "name": title, "desc": (summary + "\n" + actions_md)[:16000]},
                timeout=30,
            )
            r.raise_for_status()
            return {"status": "SENT"}

        if provider == "JIRA":
            base = _cfg(config, "baseUrl"); email = _cfg(config, "email"); api = _cfg(config, "apiToken"); project = _cfg(config, "projectKey")
            if not all([base, email, api, project]):
                return {"status": "NEEDS_CONFIG", "message": "Jira needs baseUrl + email + apiToken + projectKey"}
            r = requests.post(
                f"{base.rstrip('/')}/rest/api/2/issue",
                auth=(email, api),
                json={"fields": {"project": {"key": project}, "summary": title[:250], "description": (summary + "\n" + actions_md)[:30000], "issuetype": {"name": "Task"}}},
                timeout=30,
            )
            r.raise_for_status()
            return {"status": "SENT"}

        # ZOOM / GOOGLE_MEET are meeting-source providers (inbound); nothing to push outbound.
        if provider in {"ZOOM", "GOOGLE_MEET", "GOOGLE"}:
            return {"status": "NOOP", "message": f"{provider} is an input source, not a push target"}

        return {"status": "UNSUPPORTED", "message": f"Unknown provider {provider}"}
    except requests.RequestException as exc:
        logger.info("Integration push to %s failed: %s", provider, exc)
        return {"status": "FAILED", "message": str(exc)}
