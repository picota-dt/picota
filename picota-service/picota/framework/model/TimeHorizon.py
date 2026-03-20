from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class TimeHorizon:
    value: int
    unit: str

    @classmethod
    def from_dict(cls, data: dict[str, Any], *, default_unit: str, default_value: int) -> "TimeHorizon":
        value = FieldParser.readInt(data.get("value", default_value), fieldName="time_horizon.value", minimum=1)
        unit = str(data.get("unit", default_unit)).strip().lower()
        if unit not in {"hours", "days", "steps"}:
            raise TrainingConfigError(f"Unsupported time_horizon.unit '{unit}'")
        return cls(value=value, unit=unit)

    def to_dict(self) -> dict[str, Any]:
        return {"value": self.value, "unit": self.unit}


__all__ = ["TimeHorizon"]
