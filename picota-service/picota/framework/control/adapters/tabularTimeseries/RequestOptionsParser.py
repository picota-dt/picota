from __future__ import annotations

from typing import Any

from picota.framework.control.adapters.tabularTimeseries.AdapterOptions import AdapterOptions
from picota.framework.model.TrainingRequest import TrainingRequest


class RequestOptionsParser:
    _SUPPORTED_TIME_BUCKETS = {"years", "months", "days", "hours", "minutes", "seconds"}
    _TIME_BUCKET_ALIASES = {
        "year": "years",
        "month": "months",
        "day": "days",
        "hour": "hours",
        "minute": "minutes",
        "second": "seconds",
    }

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

        inferred_bucket = None
        if request.time_horizon.unit == "seconds":
            inferred_bucket = "seconds"
        elif request.time_horizon.unit == "minutes":
            inferred_bucket = "minutes"
        if request.time_horizon.unit == "hours":
            inferred_bucket = "hours"
        elif request.time_horizon.unit == "days":
            inferred_bucket = "days"
        elif request.time_horizon.unit == "months":
            inferred_bucket = "months"
        elif request.time_horizon.unit == "years":
            inferred_bucket = "years"

        raw_time_bucket = raw.get("time_bucket")
        time_bucket = self._normalize_time_bucket(raw_time_bucket)
        if (
                raw_time_bucket is not None
                and time_bucket is None
                and str(raw_time_bucket).strip().lower() not in {"", "none"}
        ):
            raise ValueError(f"Unsupported data_source.options.time_bucket '{raw_time_bucket}'")
        if time_bucket is None:
            time_bucket = inferred_bucket
        if request.time_horizon.unit == "steps" and time_bucket is None:
            raise ValueError(
                "time_horizon.unit='steps' requires data_source.options.time_bucket in "
                "{years, months, days, hours, minutes, seconds}"
            )
        if time_bucket not in self._SUPPORTED_TIME_BUCKETS:
            raise ValueError(f"Unsupported data_source.options.time_bucket '{time_bucket}'")
        if raw.get("time_features") is not None:
            raise ValueError(
                "data_source.options.time_features is no longer supported; "
                "use data_source.options.time_bucket only"
            )
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
            entity_key_columns=entity_key_columns,
            numerical_input_columns=numerical_input_columns,
            categorical_input_columns=categorical_input_columns,
            exclude_input_columns=exclude_input_columns,
            numerical_scaler=numerical_scaler,
            categorical_encoding=categorical_encoding,
        )

    def _normalize_time_bucket(self, raw_value: Any) -> str | None:
        if raw_value is None:
            return None
        value = str(raw_value).strip().lower()
        if not value or value == "none":
            return None
        if value in self._SUPPORTED_TIME_BUCKETS:
            return value
        return self._TIME_BUCKET_ALIASES.get(value)

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
