from __future__ import annotations

from pathlib import Path

from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.model.TrainingCommandConfig import TrainingCommandConfig
from picota.framework.model.TrainingRequest import TrainingRequest


class TrainingOutputPathResolver:
    def __init__(self, ticket_store: TicketStore, config: TrainingCommandConfig):
        self.ticket_store = ticket_store
        self.config = config

    def resolve(self, *, ticket_id: str, request: TrainingRequest) -> Path:
        _ = request
        root = self.ticket_store.ticket_dir(ticket_id)
        root.mkdir(parents=True, exist_ok=True)
        return root
