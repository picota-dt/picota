from __future__ import annotations

import logging
from typing import Any

from picota.framework.control.TrainingJobExecutor import TrainingJobExecutor
from picota.framework.control.TrainingJobWorker import TrainingJobWorker
from picota.framework.control.TrainingOutputPathResolver import TrainingOutputPathResolver
from picota.framework.control.TrainingThreadRegistry import TrainingThreadRegistry
from picota.framework.control.TrainingTicketLifecycle import TrainingTicketLifecycle
from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.model.TrainingCommandConfig import TrainingCommandConfig
from picota.framework.model.TrainingRequest import TrainingRequest

logger = logging.getLogger(__name__)


class TrainingCommander:
    def __init__(
            self,
            *,
            config: TrainingCommandConfig,
            ticket_store: TicketStore | None = None,
            job_executor: TrainingJobExecutor | None = None,
            thread_registry: TrainingThreadRegistry | None = None,
    ):
        self.config = config
        self.ticket_store = ticket_store or TicketStore(config.resolved_ticket_dir())
        self.thread_registry = thread_registry or TrainingThreadRegistry()

        output_path_resolver = TrainingOutputPathResolver(self.ticket_store, config)
        ticket_lifecycle = TrainingTicketLifecycle(self.ticket_store)
        self.job_worker = TrainingJobWorker(
            ticket_lifecycle=ticket_lifecycle,
            output_path_resolver=output_path_resolver,
            job_executor=job_executor or TrainingJobExecutor(),
        )
        logger.info("TrainingCommander initialized (ticket_dir=%s)", str(self.ticket_store.root_dir))

    def execute(self, request: TrainingRequest, request_payload: dict[str, Any] | None = None) -> dict[str, Any]:
        record = self.ticket_store.create(request_payload or request.to_dict())
        ticket_id = str(record["ticket_id"])
        logger.info(
            "Ticket created (ticket_id=%s, job_name=%s, architecture=%s/%s)",
            ticket_id,
            request.job_name,
            request.architecture.family,
            request.architecture.mode,
        )
        self.thread_registry.start(
            ticket_id=ticket_id,
            target=self._run_job,
            args=(ticket_id, request),
        )
        logger.info("Training thread started (ticket_id=%s)", ticket_id)
        return {
            "ticket_id": ticket_id,
            "status": record["status"],
            "created_at": record["created_at"],
        }

    def get_ticket(self, ticket_id: str) -> dict[str, Any] | None:
        return self.ticket_store.get(ticket_id)

    def wait(self, ticket_id: str, timeout_sec: float = 60.0, poll_sec: float = 0.1) -> dict[str, Any] | None:
        logger.info("Waiting for terminal ticket status (ticket_id=%s, timeout_sec=%.1f)", ticket_id, timeout_sec)
        result = self.thread_registry.wait_terminal(
            ticket_store=self.ticket_store,
            ticket_id=ticket_id,
            timeout_sec=timeout_sec,
            poll_sec=poll_sec,
        )
        status = None if result is None else result.get("status")
        logger.info("Wait finished (ticket_id=%s, status=%s)", ticket_id, status)
        return result

    def _run_job(self, ticket_id: str, request: TrainingRequest) -> None:
        try:
            logger.info("Job execution started (ticket_id=%s)", ticket_id)
            self.job_worker.run(ticket_id=ticket_id, request=request)
            logger.info("Job execution finished (ticket_id=%s)", ticket_id)
        finally:
            self.thread_registry.remove(ticket_id)
