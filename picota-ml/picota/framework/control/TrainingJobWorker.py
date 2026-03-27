from __future__ import annotations

import logging

from picota.framework.control.TrainingJobExecutor import TrainingJobExecutor
from picota.framework.control.TrainingOutputPathResolver import TrainingOutputPathResolver
from picota.framework.control.TrainingTicketLifecycle import TrainingTicketLifecycle
from picota.framework.model.TrainingRequest import TrainingRequest

logger = logging.getLogger(__name__)


class TrainingJobWorker:
    def __init__(
            self,
            *,
            ticket_lifecycle: TrainingTicketLifecycle,
            output_path_resolver: TrainingOutputPathResolver,
            job_executor: TrainingJobExecutor,
    ):
        self.ticket_lifecycle = ticket_lifecycle
        self.output_path_resolver = output_path_resolver
        self.job_executor = job_executor

    def run(self, *, ticket_id: str, request: TrainingRequest) -> None:
        logger.info("Marking ticket as running (ticket_id=%s)", ticket_id)
        self.ticket_lifecycle.mark_running(ticket_id, epochs_total=request.architecture.epochs)
        try:
            output_root = self.output_path_resolver.resolve(ticket_id=ticket_id, request=request)
            logger.info("Resolved output directory (ticket_id=%s, output_root=%s)", ticket_id, str(output_root))
            result = self.job_executor.execute(
                request=request,
                output_root=output_root,
                epoch_progress_listener=lambda completed, total: self.ticket_lifecycle.update_epoch_progress(
                    ticket_id,
                    epochs_completed=completed,
                    epochs_total=total,
                ),
            )
            self.ticket_lifecycle.mark_completed(ticket_id, result=result)
            logger.info("Ticket completed (ticket_id=%s)", ticket_id)
        except ValueError as exc:  # pragma: no cover
            self.ticket_lifecycle.mark_failed(ticket_id, exc=exc)
            logger.warning("Ticket rejected by validation (ticket_id=%s): %s", ticket_id, exc)
        except Exception as exc:  # pragma: no cover
            self.ticket_lifecycle.mark_failed(ticket_id, exc=exc)
            logger.exception("Ticket failed (ticket_id=%s): %s", ticket_id, exc)
