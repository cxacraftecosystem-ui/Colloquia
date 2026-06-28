"""Seed the master admin / first local admin user. Idempotent.

Run after migrations:
    python scripts/seed_admin.py
"""

import asyncio
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from app.core.config import get_settings  # noqa: E402
from app.core.db import connect_db, db, disconnect_db  # noqa: E402
from app.core.security import hash_password  # noqa: E402


async def main() -> None:
    settings = get_settings()
    await connect_db()
    try:
        email = (os.getenv("ADMIN_EMAIL") or settings.master_admin_email or "admin@example.com").lower()
        name = os.getenv("ADMIN_NAME") or "Colloquia Admin"
        password = os.getenv("ADMIN_PASSWORD") or ""
        role = "MASTER_ADMIN" if email == (settings.master_admin_email or "").lower() else "ADMIN"

        existing = await db.user.find_unique(where={"email": email})
        data = {"name": name, "role": role}
        if password:
            data["passwordHash"] = hash_password(password)
        if existing:
            await db.user.update(where={"email": email}, data=data)
            print(f"Updated user {email} (role={role})")
        else:
            await db.user.create(data={"email": email, "authProvider": "LOCAL", **data})
            print(f"Created user {email} (role={role})")

        # Ensure the master admin exists too (e.g. for Google sign-in).
        ma = (settings.master_admin_email or "").lower()
        if ma and ma != email:
            if not await db.user.find_unique(where={"email": ma}):
                await db.user.create(
                    data={"email": ma, "name": settings.master_admin_name, "role": "MASTER_ADMIN", "authProvider": "GOOGLE"}
                )
                print(f"Created master admin {ma}")
    finally:
        await disconnect_db()


if __name__ == "__main__":
    asyncio.run(main())
