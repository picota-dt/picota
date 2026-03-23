from __future__ import annotations

import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from typing import Any
from urllib.parse import urlparse

from picota.framework.view.PicotaApiController import PicotaApiController


class RuntimeTrainerRequestHandler(BaseHTTPRequestHandler):
    controller: PicotaApiController | None = None

    def do_POST(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parts = self._path_parts()
        payload = self._read_json_body()
        if payload is None:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid_json"})
            return
        if parts == ("api", "v1", "trainings"):
            status, body = self._controller().create_training(payload)
        elif parts == ("api", "v1", "inferences"):
            status, body = self._controller().create_inference(payload)
        elif parts == ("api", "v1", "cases"):
            status, body = self._controller().create_case(payload)
        elif len(parts) == 5 and parts[:3] == ("api", "v1", "cases") and parts[4] == "datasets":
            status, body = self._controller().create_case_dataset(parts[3], payload)
        elif len(parts) == 5 and parts[:3] == ("api", "v1", "cases") and parts[4] == "trainings":
            status, body = self._controller().create_case_training(parts[3], payload)
        else:
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        self._send_json(status, body)

    def do_GET(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parts = self._path_parts()
        if len(parts) == 4 and parts[:3] == ("api", "v1", "trainings"):
            ticket_id = parts[3].strip()
            if not ticket_id:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "missing_ticket_id"})
                return
            status, body = self._controller().get_training(ticket_id)
        elif len(parts) == 5 and parts[:3] == ("api", "v1", "cases") and parts[4] == "datasets":
            status, body = self._controller().list_case_datasets(parts[3])
        else:
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        self._send_json(status, body)

    def do_DELETE(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parts = self._path_parts()
        if len(parts) == 4 and parts[:3] == ("api", "v1", "cases"):
            status, body = self._controller().delete_case(parts[3])
        else:
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
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

    def _controller(self) -> PicotaApiController:
        if self.controller is None:
            raise RuntimeError("RuntimeTrainerRequestHandler.controller is not configured")
        return self.controller

    def _path_parts(self) -> tuple[str, ...]:
        parsed = urlparse(self.path)
        return tuple(part for part in parsed.path.split("/") if part)
