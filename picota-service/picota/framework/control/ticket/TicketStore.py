from __future__ import annotations

import json
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class TicketStore:
    STATUS_QUEUED = "queued"
    STATUS_RUNNING = "running"
    STATUS_COMPLETED = "completed"
    STATUS_FAILED = "failed"

    def __init__(self, root_dir: Path):
        self.root_dir = root_dir.resolve()
        self.root_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()

    def create(self, request_payload: dict[str, Any]) -> dict[str, Any]:
        ticket_id = uuid.uuid4().hex
        record = {
            "ticket_id": ticket_id,
            "status": self.STATUS_QUEUED,
            "created_at": self._utc_now_iso(),
            "updated_at": self._utc_now_iso(),
            "request": request_payload,
            "result": None,
            "error": None,
            "history": [
                {
                    "status": self.STATUS_QUEUED,
                    "at": self._utc_now_iso(),
                }
            ],
        }
        with self._lock:
            self._write_record(ticket_id=ticket_id, record=record)
        return record

    def update(self, ticket_id: str, **patch: Any) -> dict[str, Any]:
        with self._lock:
            current = self._read_record(ticket_id)
            current.update(patch)
            current["updated_at"] = self._utc_now_iso()
            status = patch.get("status")
            if status:
                history = list(current.get("history") or [])
                history.append({"status": status, "at": self._utc_now_iso()})
                current["history"] = history
            self._write_record(ticket_id=ticket_id, record=current)
            return current

    def get(self, ticket_id: str) -> dict[str, Any] | None:
        try:
            return self._read_record(ticket_id)
        except FileNotFoundError:
            return None

    @staticmethod
    def _utc_now_iso() -> str:
        return datetime.now(timezone.utc).isoformat()

    def _ticket_path(self, ticket_id: str) -> Path:
        return self.root_dir / f"{ticket_id}.json"

    def _read_record(self, ticket_id: str) -> dict[str, Any]:
        ticket_path = self._ticket_path(ticket_id)
        with ticket_path.open("r", encoding="utf-8") as handle:
            return json.load(handle)

    def _write_record(self, ticket_id: str, record: dict[str, Any]) -> None:
        ticket_path = self._ticket_path(ticket_id)
        temp_path = self._ticket_path(f".{ticket_id}.tmp")
        with temp_path.open("w", encoding="utf-8") as handle:
            json.dump(record, handle, ensure_ascii=True, indent=2, sort_keys=True)
        temp_path.replace(ticket_path)


__all__ = ["TicketStore"]
