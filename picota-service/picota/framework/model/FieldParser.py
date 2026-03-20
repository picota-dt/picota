from __future__ import annotations

from typing import Any

from picota.framework.model.TrainingConfigError import TrainingConfigError


class FieldParser:
    @staticmethod
    def readInt(value: Any, *, fieldName: str, minimum: int | None = None) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError) as exc:
            raise TrainingConfigError(f"Invalid integer for '{fieldName}': {value!r}") from exc
        if minimum is not None and parsed < minimum:
            raise TrainingConfigError(f"'{fieldName}' must be >= {minimum}, got {parsed}")
        return parsed

    @staticmethod
    def readFloat(value: Any, *, fieldName: str, minimum: float | None = None) -> float:
        try:
            parsed = float(value)
        except (TypeError, ValueError) as exc:
            raise TrainingConfigError(f"Invalid float for '{fieldName}': {value!r}") from exc
        if minimum is not None and parsed < minimum:
            raise TrainingConfigError(f"'{fieldName}' must be >= {minimum}, got {parsed}")
        return parsed
