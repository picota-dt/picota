from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class SplitConfig:
    train_ratio: float = 0.64
    val_ratio: float = 0.16
    test_ratio: float = 0.20

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None) -> "SplitConfig":
        data = data or {}
        train_ratio = FieldParser.readFloat(data.get("train_ratio", 0.64), fieldName="split.train_ratio", minimum=0.0)
        val_ratio = FieldParser.readFloat(data.get("val_ratio", 0.16), fieldName="split.val_ratio", minimum=0.0)
        test_ratio = FieldParser.readFloat(data.get("test_ratio", 0.20), fieldName="split.test_ratio", minimum=0.0)
        total = train_ratio + val_ratio + test_ratio
        if abs(total - 1.0) > 1e-6:
            raise TrainingConfigError("split.train_ratio + split.val_ratio + split.test_ratio must be 1.0")
        return cls(train_ratio=train_ratio, val_ratio=val_ratio, test_ratio=test_ratio)

    def to_dict(self) -> dict[str, Any]:
        return {
            "train_ratio": self.train_ratio,
            "val_ratio": self.val_ratio,
            "test_ratio": self.test_ratio,
        }


__all__ = ["SplitConfig"]
