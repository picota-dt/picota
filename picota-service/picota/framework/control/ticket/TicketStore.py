from __future__ import annotations

import json
import logging
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


class TicketStore:
    STATUS_QUEUED = "queued"
    STATUS_RUNNING = "running"
    STATUS_COMPLETED = "completed"
    STATUS_FAILED = "failed"

    REQUEST_FILE_NAME = "request.json"
    RESULT_FILE_NAME = "result.json"

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
            self.ticket_dir(ticket_id).mkdir(parents=True, exist_ok=False)
            self._write_request_payload(ticket_id=ticket_id, request_payload=request_payload)
            self._write_result_record(ticket_id=ticket_id, record=record)
        logger.info("Ticket persisted (ticket_id=%s, status=%s)", ticket_id, self.STATUS_QUEUED)
        return self.get(ticket_id) or {}

    def update(self, ticket_id: str, **patch: Any) -> dict[str, Any]:
        with self._lock:
            current = self._read_result_record(ticket_id)
            request_patch = patch.pop("request", None)
            if request_patch is not None:
                if not isinstance(request_patch, dict):
                    raise ValueError("Ticket request patch must be an object")
                self._write_request_payload(ticket_id=ticket_id, request_payload=request_patch)

            current.update(patch)
            current["updated_at"] = self._utc_now_iso()
            status = patch.get("status")
            if status:
                history = list(current.get("history") or [])
                history.append({"status": status, "at": self._utc_now_iso()})
                current["history"] = history
            self._write_result_record(ticket_id=ticket_id, record=current)
            status = patch.get("status", current.get("status"))
            logger.info("Ticket updated (ticket_id=%s, status=%s)", ticket_id, status)
            return self._build_response(ticket_id=ticket_id, result_record=current)

    def get(self, ticket_id: str) -> dict[str, Any] | None:
        try:
            result_record = self._read_result_record(ticket_id)
            return self._build_response(ticket_id=ticket_id, result_record=result_record)
        except FileNotFoundError:
            return None

    @staticmethod
    def _utc_now_iso() -> str:
        return datetime.now(timezone.utc).isoformat()

    def ticket_dir(self, ticket_id: str) -> Path:
        return self.root_dir / ticket_id

    def _request_path(self, ticket_id: str) -> Path:
        return self.ticket_dir(ticket_id) / self.REQUEST_FILE_NAME

    def _result_path(self, ticket_id: str) -> Path:
        return self.ticket_dir(ticket_id) / self.RESULT_FILE_NAME

    def _read_result_record(self, ticket_id: str) -> dict[str, Any]:
        result_path = self._result_path(ticket_id)
        with result_path.open("r", encoding="utf-8") as handle:
            return json.load(handle)

    def _read_request_payload(self, ticket_id: str) -> dict[str, Any]:
        request_path = self._request_path(ticket_id)
        with request_path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        if not isinstance(payload, dict):
            raise ValueError(f"Invalid request payload for ticket {ticket_id}")
        return payload

    def _write_request_payload(self, *, ticket_id: str, request_payload: dict[str, Any]) -> None:
        self._write_json_atomic(self._request_path(ticket_id), request_payload)

    def _write_result_record(self, *, ticket_id: str, record: dict[str, Any]) -> None:
        self._write_json_atomic(self._result_path(ticket_id), record)

    @staticmethod
    def _write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
        temp_path = path.parent / f".{path.name}.tmp"
        with temp_path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=True, indent=2, sort_keys=True)
        temp_path.replace(path)

    def _build_response(self, *, ticket_id: str, result_record: dict[str, Any]) -> dict[str, Any]:
        response = dict(result_record)
        if "request" not in response or not isinstance(response.get("request"), dict):
            response["request"] = self._read_request_payload(ticket_id)
        return response


__all__ = ["TicketStore"]
