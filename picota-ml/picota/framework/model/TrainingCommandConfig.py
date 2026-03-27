from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class TrainingCommandConfig:
    ticket_dir: Path
    artifacts_subdir: str = "artifacts"

    def resolved_ticket_dir(self) -> Path:
        return self.ticket_dir.expanduser().resolve()
