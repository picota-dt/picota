from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class DataSourceConfig:
    kind: str
    path: str | None = None
    dataset_id: str | None = None
    limit_rows: int | None = None
    options: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "DataSourceConfig":
        if not isinstance(data, dict):
            raise TrainingConfigError("data_source must be an object")
        kind = str(data.get("kind", "")).strip().lower()
        if kind not in {"tabular_timeseries"}:
            raise TrainingConfigError(f"Unsupported data_source.kind '{kind}'")
        path = data.get("path")
        if path is not None:
            path = str(path)
        dataset_id = data.get("dataset_id")
        if dataset_id is not None:
            dataset_id = str(dataset_id).strip()
            if not dataset_id:
                raise TrainingConfigError("data_source.dataset_id must not be empty")
        limit_rows = data.get("limit_rows")
        if limit_rows is not None:
            limit_rows = FieldParser.readInt(limit_rows, fieldName="data_source.limit_rows", minimum=1)
        options = data.get("options") or {}
        if not isinstance(options, dict):
            raise TrainingConfigError("data_source.options must be an object")
        return cls(
            kind=kind,
            path=path,
            dataset_id=dataset_id,
            limit_rows=limit_rows,
            options=dict(options),
        )

    def resolved_path(self) -> Path | None:
        if self.path is None:
            return None
        return Path(self.path).expanduser().resolve()

    def to_dict(self) -> dict[str, Any]:
        return {
            "kind": self.kind,
            "path": self.path,
            "dataset_id": self.dataset_id,
            "limit_rows": self.limit_rows,
            "options": dict(self.options),
        }


__all__ = ["DataSourceConfig"]
