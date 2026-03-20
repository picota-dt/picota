from __future__ import annotations

from picota.framework.control.TrainingJobExecutor import TrainingJobExecutor
from picota.framework.control.TrainingOutputPathResolver import TrainingOutputPathResolver
from picota.framework.control.TrainingTicketLifecycle import TrainingTicketLifecycle
from picota.framework.model.TrainingRequest import TrainingRequest


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
        self.ticket_lifecycle.mark_running(ticket_id)
        try:
            output_root = self.output_path_resolver.resolve(ticket_id=ticket_id, request=request)
            result = self.job_executor.execute(request=request, output_root=output_root)
            self.ticket_lifecycle.mark_completed(ticket_id, result=result)
        except Exception as exc:  # pragma: no cover
            self.ticket_lifecycle.mark_failed(ticket_id, exc=exc)
