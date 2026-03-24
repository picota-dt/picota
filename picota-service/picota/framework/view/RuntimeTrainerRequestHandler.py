from __future__ import annotations

import json
import logging
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler
from typing import Any
from urllib.parse import urlparse

from picota.framework.view.PicotaApiController import PicotaApiController

logger = logging.getLogger(__name__)


class RuntimeTrainerRequestHandler(BaseHTTPRequestHandler):
    controller: PicotaApiController | None = None
    log_api_requests: bool = False

    def do_POST(self) -> None:  # pragma: no cover - exercised by integration test through network loop
        parts = self._path_parts()
        payload, raw_payload = self._read_json_body()
        self._log_api_request(payload=payload, raw_payload=raw_payload)
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
        self._log_api_request()
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
        self._log_api_request()
        if len(parts) == 4 and parts[:3] == ("api", "v1", "cases"):
            status, body = self._controller().delete_case(parts[3])
        else:
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "not_found"})
            return
        self._send_json(status, body)

    def log_message(self, format: str, *args) -> None:
        _ = (format, args)

    def _read_json_body(self) -> tuple[dict[str, Any] | None, str | None]:
        raw_length = self.headers.get("Content-Length", "0")
        try:
            length = int(raw_length)
        except ValueError:
            return None, None
        if length <= 0:
            return None, None
        payload = self.rfile.read(length)
        decoded_text = payload.decode("utf-8", errors="replace")
        try:
            decoded = json.loads(decoded_text)
        except json.JSONDecodeError:
            return None, decoded_text
        if not isinstance(decoded, dict):
            return None, decoded_text
        return decoded, decoded_text

    def _send_json(self, status: int, body: dict[str, Any]) -> None:
        encoded = json.dumps(body, ensure_ascii=True).encode("utf-8")
        self.send_response(int(status))
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)
        self._log_api_response(status=int(status), body=body)

    def _controller(self) -> PicotaApiController:
        if self.controller is None:
            raise RuntimeError("RuntimeTrainerRequestHandler.controller is not configured")
        return self.controller

    def _path_parts(self) -> tuple[str, ...]:
        parsed = urlparse(self.path)
        return tuple(part for part in parsed.path.split("/") if part)

    def _log_api_request(
            self,
            payload: dict[str, Any] | None = None,
            raw_payload: str | None = None,
    ) -> None:
        if not self.log_api_requests:
            return
        if payload is None:
            if raw_payload is not None and raw_payload.strip():
                logger.info(
                    "API request method=%s path=%s client=%s raw_payload=%s",
                    self.command,
                    self._request_path(),
                    self._client_ip(),
                    self._truncate(raw_payload),
                )
                return
            logger.info(
                "API request method=%s path=%s client=%s",
                self.command,
                self._request_path(),
                self._client_ip(),
            )
            return
        logger.info(
            "API request method=%s path=%s client=%s payload=%s",
            self.command,
            self._request_path(),
            self._client_ip(),
            self._serialize_payload(payload),
        )

    def _log_api_response(self, status: int, body: dict[str, Any]) -> None:
        if not self.log_api_requests:
            return
        logger.info(
            "API response method=%s path=%s client=%s status=%s payload=%s",
            self.command,
            self._request_path(),
            self._client_ip(),
            int(status),
            self._serialize_payload(body),
        )

    def _request_path(self) -> str:
        parsed = urlparse(self.path)
        if parsed.query:
            return f"{parsed.path}?{parsed.query}"
        return parsed.path

    def _client_ip(self) -> str:
        if isinstance(self.client_address, tuple) and len(self.client_address) > 0:
            return str(self.client_address[0])
        return "-"

    @staticmethod
    def _serialize_payload(payload: dict[str, Any]) -> str:
        text = json.dumps(payload, ensure_ascii=True, separators=(",", ":"))
        return RuntimeTrainerRequestHandler._truncate(text)

    @staticmethod
    def _truncate(value: str, max_len: int = 2000) -> str:
        if value is None:
            return ""
        if len(value) <= max_len:
            return value
        return value[:max_len] + "..."
