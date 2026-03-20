from __future__ import annotations

import traceback
from datetime import datetime, timezone
from typing import Any

from picota.framework.control.ticket.TicketStore import TicketStore


class TrainingTicketLifecycle:
    def __init__(self, ticket_store: TicketStore):
        self.ticket_store = ticket_store

    def mark_running(self, ticket_id: str) -> None:
        self.ticket_store.update(
            ticket_id,
            status=TicketStore.STATUS_RUNNING,
            started_at=self._now(),
        )

    def mark_completed(self, ticket_id: str, *, result: dict[str, Any]) -> None:
        self.ticket_store.update(
            ticket_id,
            status=TicketStore.STATUS_COMPLETED,
            finished_at=self._now(),
            result=result,
            error=None,
        )

    def mark_failed(self, ticket_id: str, exc: Exception) -> None:
        self.ticket_store.update(
            ticket_id,
            status=TicketStore.STATUS_FAILED,
            finished_at=self._now(),
            error={
                "type": type(exc).__name__,
                "message": str(exc),
                "traceback": traceback.format_exc(),
            },
            result=None,
        )

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat()
