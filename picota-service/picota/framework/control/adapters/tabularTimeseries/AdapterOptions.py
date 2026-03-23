from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class AdapterOptions:
    case_name: str
    timestamp_column: str
    target_column: str
    delimiter: str | None
    time_bucket: str
    entity_key_columns: list[str]
    numerical_input_columns: list[str] | None
    categorical_input_columns: list[str] | None
    exclude_input_columns: list[str]
    numerical_scaler: str
    categorical_encoding: str
