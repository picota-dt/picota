from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime


@dataclass
class ExampleRow:
    instant: datetime
    numerical_values: dict[str, float]
    categorical_values: dict[str, str]
    target_future: float
