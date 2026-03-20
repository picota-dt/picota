from __future__ import annotations

from typing import Any

from picota.framework.control.adapters.tabularTimeseries.AdapterOptions import AdapterOptions
from picota.framework.model.TrainingRequest import TrainingRequest


class RequestOptionsParser:
    def parse(self, request: TrainingRequest) -> AdapterOptions:
        raw = request.data_source.options or {}
        if not isinstance(raw, dict):
            raise ValueError("data_source.options must be an object")

        timestamp_column = str(raw.get("timestamp_column") or "instant").strip()
        target_column = str(raw.get("target_column") or request.target_variable).strip()
        if not timestamp_column:
            raise ValueError("data_source.options.timestamp_column must not be empty")
        if not target_column:
            raise ValueError("data_source.options.target_column must not be empty")

        delimiter = raw.get("delimiter")
        if delimiter is not None:
            delimiter = str(delimiter)

        inferred_bucket = "none"
        if request.time_horizon.unit == "hours":
            inferred_bucket = "hour"
        elif request.time_horizon.unit == "days":
            inferred_bucket = "day"
        time_bucket = str(raw.get("time_bucket") or inferred_bucket).strip().lower()
        if time_bucket not in {"none", "hour", "day"}:
            raise ValueError(f"Unsupported data_source.options.time_bucket '{time_bucket}'")

        inferred_time_features = "none"
        if time_bucket == "hour":
            inferred_time_features = "hourly"
        elif time_bucket == "day":
            inferred_time_features = "daily"
        time_features = str(raw.get("time_features") or inferred_time_features).strip().lower()
        if time_features not in {"none", "hourly", "daily"}:
            raise ValueError(f"Unsupported data_source.options.time_features '{time_features}'")

        case_name = str(raw.get("case_name") or request.job_name).strip()
        if not case_name:
            case_name = request.job_name

        entity_key_columns = self._read_str_list(raw.get("entity_key_columns"), field_name="entity_key_columns")
        numerical_input_columns = self._read_optional_str_list(
            raw.get("numerical_input_columns"),
            field_name="numerical_input_columns",
        )
        categorical_input_columns = self._read_optional_str_list(
            raw.get("categorical_input_columns"),
            field_name="categorical_input_columns",
        )
        exclude_input_columns = self._read_str_list(
            raw.get("exclude_input_columns"),
            field_name="exclude_input_columns",
        )

        numerical_scaler = str(raw.get("numerical_scaler") or "zscore").strip().lower()
        if numerical_scaler not in {"zscore", "minmax", "none"}:
            raise ValueError(f"Unsupported data_source.options.numerical_scaler '{numerical_scaler}'")

        categorical_encoding = str(raw.get("categorical_encoding") or "one_hot").strip().lower()
        if categorical_encoding not in {"one_hot", "none"}:
            raise ValueError(f"Unsupported data_source.options.categorical_encoding '{categorical_encoding}'")

        return AdapterOptions(
            case_name=case_name,
            timestamp_column=timestamp_column,
            target_column=target_column,
            delimiter=delimiter,
            time_bucket=time_bucket,
            time_features=time_features,
            entity_key_columns=entity_key_columns,
            numerical_input_columns=numerical_input_columns,
            categorical_input_columns=categorical_input_columns,
            exclude_input_columns=exclude_input_columns,
            numerical_scaler=numerical_scaler,
            categorical_encoding=categorical_encoding,
        )

    @staticmethod
    def _read_str_list(value: Any, *, field_name: str) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ValueError(f"data_source.options.{field_name} must be a list")
        parsed: list[str] = []
        for index, item in enumerate(value):
            text = str(item).strip()
            if not text:
                raise ValueError(f"data_source.options.{field_name}[{index}] must not be empty")
            parsed.append(text)
        return parsed

    def _read_optional_str_list(self, value: Any, *, field_name: str) -> list[str] | None:
        if value is None:
            return None
        return self._read_str_list(value, field_name=field_name)
