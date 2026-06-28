"""Custom automation rules: when a trigger fires for a recording, run the rule's action.

Runs from the queue worker (after transcription/analysis) and from a manual endpoint. Background runs
can only perform actions that need no per-user OAuth token (push to Slack/Teams/webhook, Notion/Trello/
Jira with stored tokens). Token-bound Google actions (tasks/email/calendar) are recorded as skipped in
the background and are available interactively via the productivity routes.
"""

import logging
from typing import Any

from app.core.db import db
from app.services import integrations

logger = logging.getLogger(__name__)

PROVIDER_FOR_ACTION = {
    "PUSH_SLACK": "SLACK",
    "PUSH_NOTION": "NOTION",
    "PUSH_TRELLO": "TRELLO",
    "PUSH_JIRA": "JIRA",
    "PUSH_TEAMS": "TEAMS",
}


async def _payload_for(recording_id: str) -> dict[str, Any]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    summary = await db.summary.find_first(where={"recordingId": recording_id, "type": "BRIEF"})
    actions = await db.actionitem.find_many(where={"recordingId": recording_id, "kind": "ACTION"})
    return {
        "title": (rec.aiTitle or rec.title) if rec else "Recording",
        "summary": summary.content if summary else "",
        "actionItems": [a.text for a in actions],
    }


async def run_rules_for_recording(recording_id: str, trigger: str) -> list[dict[str, Any]]:
    rec = await db.recording.find_unique(where={"id": recording_id})
    if not rec:
        return []
    rules = await db.automationrule.find_many(
        where={"userId": rec.userId, "enabled": True, "trigger": trigger}
    )
    if not rules:
        return []
    payload = await _payload_for(recording_id)
    results: list[dict[str, Any]] = []
    for rule in rules:
        results.append(await _run_one(rule, payload, recording_id))
    return results


async def _run_one(rule: Any, payload: dict[str, Any], recording_id: str) -> dict[str, Any]:
    action = str(rule.action).upper()
    try:
        if action == "WEBHOOK":
            url = (rule.config or {}).get("url") if isinstance(rule.config, dict) else None
            if not url:
                return {"rule": rule.id, "status": "NEEDS_CONFIG"}
            res = integrations.push_to_provider("WEBHOOK", {"webhookUrl": url}, payload)
            return {"rule": rule.id, **res}

        if action in PROVIDER_FOR_ACTION:
            provider = PROVIDER_FOR_ACTION[action]
            integration = await db.integration.find_unique(
                where={"userId_provider": {"userId": rule.userId, "provider": provider}}
            )
            if not integration or not integration.enabled:
                return {"rule": rule.id, "status": "NEEDS_CONFIG", "message": f"No {provider} integration"}
            res = integrations.push_to_provider(provider, integration.config, payload)
            return {"rule": rule.id, **res}

        # Token-bound Google actions can't run headless; surfaced interactively instead.
        if action in {"CREATE_TASKS", "EMAIL_DRAFT", "RUN_TEMPLATE"}:
            return {"rule": rule.id, "status": "SKIPPED_INTERACTIVE", "action": action}

        return {"rule": rule.id, "status": "UNSUPPORTED"}
    except Exception as exc:  # noqa: BLE001 - one rule failing must not break the worker
        logger.info("Automation rule %s failed: %s", rule.id, exc)
        return {"rule": rule.id, "status": "FAILED", "message": str(exc)}
