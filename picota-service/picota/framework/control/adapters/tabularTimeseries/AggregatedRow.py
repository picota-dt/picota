from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class AggregatedRow:
    instant: datetime
    entity_key: tuple[str, ...]
    numerical_values: dict[str, float]
    categorical_values: dict[str, str]
    target_value: float
