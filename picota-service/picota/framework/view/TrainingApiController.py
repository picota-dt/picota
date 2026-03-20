from __future__ import annotations

from http import HTTPStatus
from typing import Any

from picota.framework.control.TrainingCommander import TrainingCommander
from picota.framework.model.TrainingConfigError import TrainingConfigError
from picota.framework.model.TrainingRequest import TrainingRequest


class TrainingApiController:
    def __init__(self, commander: TrainingCommander):
        self.commander = commander

    def create_training(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        try:
            request = TrainingRequest.from_dict(payload)
            created = self.commander.execute(request)
        except TrainingConfigError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        return HTTPStatus.ACCEPTED, {
            "ticket_id": created["ticket_id"],
            "status": created["status"],
            "created_at": created["created_at"],
            "links": {"self": f"/api/v1/trainings/{created['ticket_id']}"},
        }

    def get_training(self, ticket_id: str) -> tuple[int, dict[str, Any]]:
        record = self.commander.get_ticket(ticket_id)
        if record is None:
            return HTTPStatus.NOT_FOUND, {"error": "ticket_not_found", "ticket_id": ticket_id}
        return HTTPStatus.OK, record
