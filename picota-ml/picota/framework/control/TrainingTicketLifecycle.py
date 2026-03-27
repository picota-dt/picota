from __future__ import annotations

import traceback
from datetime import datetime, timezone
from typing import Any

from picota.framework.control.ticket.TicketStore import TicketStore


class TrainingTicketLifecycle:
    def __init__(self, ticket_store: TicketStore):
        self.ticket_store = ticket_store

    def mark_running(self, ticket_id: str, *, epochs_total: int | None = None) -> None:
        payload: dict[str, Any] = {
            "status": TicketStore.STATUS_RUNNING,
            "started_at": self._now(),
        }
        normalized_total = self._positive_int(epochs_total)
        if normalized_total is not None:
            payload["progress"] = {
                "percent": 0,
                "epochs_completed": 0,
                "epochs_total": normalized_total,
            }
        self.ticket_store.update(ticket_id, **payload)

    def mark_completed(self, ticket_id: str, *, result: dict[str, Any]) -> None:
        payload: dict[str, Any] = {
            "status": TicketStore.STATUS_COMPLETED,
            "finished_at": self._now(),
            "result": result,
            "error": None,
        }
        normalized_total = self._epochs_total_from_result(result)
        if normalized_total is not None:
            payload["progress"] = {
                "percent": 100,
                "epochs_completed": normalized_total,
                "epochs_total": normalized_total,
            }
        self.ticket_store.update(
            ticket_id,
            **payload,
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

    def update_epoch_progress(self, ticket_id: str, *, epochs_completed: int, epochs_total: int) -> None:
        normalized_total = self._positive_int(epochs_total)
        normalized_completed = self._non_negative_int(epochs_completed)
        if normalized_total is None or normalized_completed is None:
            return
        completed = min(normalized_completed, normalized_total)
        percent = int(round((completed / float(normalized_total)) * 100.0))
        self.ticket_store.update(
            ticket_id,
            progress={
                "percent": max(0, min(100, percent)),
                "epochs_completed": completed,
                "epochs_total": normalized_total,
            },
        )

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _positive_int(value: Any) -> int | None:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return None
        return parsed if parsed > 0 else None

    @staticmethod
    def _non_negative_int(value: Any) -> int | None:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return None
        return parsed if parsed >= 0 else None

    def _epochs_total_from_result(self, result: dict[str, Any]) -> int | None:
        if not isinstance(result, dict):
            return None
        request = result.get("request")
        if not isinstance(request, dict):
            return None
        architecture = request.get("architecture")
        if not isinstance(architecture, dict):
            return None
        return self._positive_int(architecture.get("epochs"))
