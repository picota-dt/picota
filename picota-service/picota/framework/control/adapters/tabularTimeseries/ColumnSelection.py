from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ColumnSelection:
    numerical_columns: list[str]
    categorical_columns: list[str]
