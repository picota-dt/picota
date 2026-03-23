from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class InferenceRequest:
    training_ticket_id: str
    instances: list[dict[str, Any]]
    output_scale: str = "raw"

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "InferenceRequest":
        if not isinstance(data, dict):
            raise TrainingConfigError("Inference request payload must be an object")
        training_ticket_id = str(data.get("training_ticket_id") or "").strip()
        if not training_ticket_id:
            raise TrainingConfigError("training_ticket_id is required")

        raw_instances = data.get("instances")
        if not isinstance(raw_instances, list) or len(raw_instances) == 0:
            raise TrainingConfigError("instances must be a non-empty list")
        instances: list[dict[str, Any]] = []
        for index, item in enumerate(raw_instances):
            if not isinstance(item, dict):
                raise TrainingConfigError(f"instances[{index}] must be an object")
            instances.append(dict(item))

        output_scale = str(data.get("output_scale") or "raw").strip().lower()
        if output_scale not in {"raw", "normalized", "both"}:
            raise TrainingConfigError("output_scale must be one of: raw, normalized, both")
        return cls(
            training_ticket_id=training_ticket_id,
            instances=instances,
            output_scale=output_scale,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "training_ticket_id": self.training_ticket_id,
            "instances": list(self.instances),
            "output_scale": self.output_scale,
        }


__all__ = ["InferenceRequest"]
