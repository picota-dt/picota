from __future__ import annotations

from typing import Any

from picota.framework.control.ModelInferenceRunner import ModelInferenceRunner
from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.model.InferenceRequest import InferenceRequest


class ModelInferenceCommander:
    def __init__(
            self,
            *,
            ticket_store: TicketStore,
            runner: ModelInferenceRunner | None = None,
    ):
        self.ticket_store = ticket_store
        self.runner = runner or ModelInferenceRunner()

    def execute(self, request: InferenceRequest) -> dict[str, Any]:
        training_record = self.ticket_store.get(request.training_ticket_id)
        if training_record is None:
            raise FileNotFoundError(f"Training ticket not found: {request.training_ticket_id}")
        status = str(training_record.get("status"))
        if status != TicketStore.STATUS_COMPLETED:
            raise ValueError(
                f"Training ticket '{request.training_ticket_id}' must be completed to infer; current status: {status}"
            )
        return self.runner.run(training_record=training_record, request=request)


__all__ = ["ModelInferenceCommander"]
