from __future__ import annotations

import logging
from collections.abc import Callable
from pathlib import Path
from typing import Any

from picota.framework.control.training.TrainingRunner import TrainingRunner
from picota.framework.model.TrainingRequest import TrainingRequest

logger = logging.getLogger(__name__)


class TrainingJobExecutor:
    def __init__(self, runner: TrainingRunner | None = None):
        self.runner = runner or TrainingRunner()

    def execute(
            self,
            *,
            request: TrainingRequest,
            output_root: Path,
            epoch_progress_listener: Callable[[int, int], None] | None = None,
    ) -> dict[str, Any]:
        logger.info(
            "Executing training job (job_name=%s, output_root=%s)",
            request.job_name,
            str(output_root),
        )
        return self.runner.run(
            request=request,
            output_root=output_root,
            epoch_progress_listener=epoch_progress_listener,
        )
