from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class PreparedTrainingData:
    case_name: str
    input_variables: list[str]
    output_variable: str
    lookback: int
    means: list[float]
    stds: list[float]
    out_min: float
    out_max: float
    train_items: list[dict[str, Any]]
    val_items: list[dict[str, Any]]
    test_items: list[dict[str, Any]]
    metadata: dict[str, Any] = field(default_factory=dict)


__all__ = ["PreparedTrainingData"]
