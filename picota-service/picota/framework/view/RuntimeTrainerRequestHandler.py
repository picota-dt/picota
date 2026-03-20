from __future__ import annotations

import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from typing import Any
from urllib.parse import urlparse

from picota.framework.view.TrainingApiController import TrainingApiController


class RuntimeTrainerRequestHandler(BaseHTTPRequestHandler):
    controller: TrainingApiController | None = None

    def do_POST(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parsed = urlparse(self.path)
        if parsed.path != "/api/v1/trainings":
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        payload = self._read_json_body()
        if payload is None:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
            return
        status, body = self._controller().create_training(payload)
        self._send_json(status, body)

    def do_GET(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parsed = urlparse(self.path)
        prefix = "/api/v1/trainings/"
        if not parsed.path.startswith(prefix):
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        ticket_id = parsed.path[len(prefix):].strip()
        if not ticket_id:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "missing_ticket_id"})
            return
        status, body = self._controller().get_training(ticket_id)
        self._send_json(status, body)

    def log_message(self, format: str, *args) -> None:
        _ = (format, args)

    def _read_json_body(self) -> dict[str, Any] | None:
        raw_length = self.headers.get("Content-Length", "0")
        try:
            length = int(raw_length)
        except ValueError:
            return None
        if length <= 0:
            return None
        payload = self.rfile.read(length)
        try:
            decoded = json.loads(payload.decode("utf-8"))
        except json.JSONDecodeError:
            return None
        return decoded if isinstance(decoded, dict) else None

    def _send_json(self, status: int, body: dict[str, Any]) -> None:
        encoded = json.dumps(body, ensure_ascii=True).encode("utf-8")
        self.send_response(int(status))
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _controller(self) -> TrainingApiController:
        if self.controller is None:
            raise RuntimeError("RuntimeTrainerRequestHandler.controller is not configured")
        return self.controller
