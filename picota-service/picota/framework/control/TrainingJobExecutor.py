from __future__ import annotations

from pathlib import Path
from typing import Any

from picota.framework.control.training.TrainingRunner import TrainingRunner
from picota.framework.model.TrainingRequest import TrainingRequest


class TrainingJobExecutor:
    def __init__(self, runner: TrainingRunner | None = None):
        self.runner = runner or TrainingRunner()

    def execute(self, *, request: TrainingRequest, output_root: Path) -> dict[str, Any]:
        return self.runner.run(request=request, output_root=output_root)
