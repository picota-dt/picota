from __future__ import annotations

import math
from datetime import datetime
from typing import Any, Callable

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow

HOURLY_TIME_FEATURE_NAMES = [
    "month_sin",
    "month_cos",
    "day_sin",
    "day_cos",
    "hour_sin",
    "hour_cos",
    "quarter_sin",
    "quarter_cos",
]

DAILY_TIME_FEATURE_NAMES = [
    "month_sin",
    "month_cos",
    "day_sin",
    "day_cos",
    "week_sin",
    "week_cos",
    "quarter_sin",
    "quarter_cos",
]


class ItemEncoder:
    def __init__(
            self,
            *,
            time_features_kind: str,
            numerical_columns: list[str],
            numerical_value_getter: Callable[[ExampleRow, str], float],
            one_hot_map: dict[str, list[str]],
            target_min: float,
            target_max: float,
    ):
        self.time_features_kind = time_features_kind
        self.numerical_columns = numerical_columns
        self.numerical_value_getter = numerical_value_getter
        self.one_hot_map = one_hot_map
        self.target_min = float(target_min)
        self.target_max = float(target_max)

    def build_items(self, rows: list[ExampleRow]) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        for row in rows:
            t_features = self.encode_time_features(row.instant, time_features_kind=self.time_features_kind)
            numerical_t_features = [float(self.numerical_value_getter(row, col)) for col in self.numerical_columns]
            categorical_t_features = self.one_hot_encode(row.categorical_values, self.one_hot_map)
            out = self.normalize_minmax(row.target_future, min_value=self.target_min, max_value=self.target_max)
            items.append(
                {
                    "out": float(out),
                    "t": [float(v) for v in t_features],
                    "categorical_t_features": [float(v) for v in categorical_t_features],
                    "numerical_t_features": [float(v) for v in numerical_t_features],
                    "lookback_t": [],
                    "categorical_lookback_features": [],
                    "numerical_lookback_features": [],
                }
            )
        return items

    @staticmethod
    def build_one_hot_map(rows: list[ExampleRow], categorical_columns: list[str]) -> dict[str, list[str]]:
        mapping: dict[str, list[str]] = {}
        for col in categorical_columns:
            mapping[col] = sorted({str(row.categorical_values.get(col, "")) for row in rows})
        return mapping

    @staticmethod
    def build_one_hot_feature_names(one_hot_map: dict[str, list[str]]) -> list[str]:
        names: list[str] = []
        for col, categories in one_hot_map.items():
            for category in categories:
                names.append(f"{col}={category}")
        return names

    @staticmethod
    def time_features_kind_from_bucket(time_bucket: str) -> str:
        if time_bucket in {"hours", "minutes", "seconds", "hour", "minute", "second"}:
            return "hourly"
        if time_bucket in {"days", "months", "years", "day", "month", "year"}:
            return "daily"
        return "none"

    @staticmethod
    def time_feature_names(time_features_kind: str) -> list[str]:
        if time_features_kind == "hourly":
            return list(HOURLY_TIME_FEATURE_NAMES)
        if time_features_kind == "daily":
            return list(DAILY_TIME_FEATURE_NAMES)
        return []

    @staticmethod
    def sin_cos(value: float, period: float) -> tuple[float, float]:
        angle = (2.0 * math.pi * value) / period
        return math.sin(angle), math.cos(angle)

    @classmethod
    def encode_time_features(cls, timestamp: datetime, *, time_features_kind: str) -> list[float]:
        if time_features_kind == "hourly":
            month_sin, month_cos = cls.sin_cos(timestamp.month - 1, 12.0)
            day_sin, day_cos = cls.sin_cos(timestamp.day - 1, 31.0)
            hour_sin, hour_cos = cls.sin_cos(timestamp.hour, 24.0)
            quarter_sin, quarter_cos = cls.sin_cos((timestamp.month - 1) // 3, 4.0)
            return [
                month_sin,
                month_cos,
                day_sin,
                day_cos,
                hour_sin,
                hour_cos,
                quarter_sin,
                quarter_cos,
            ]
        if time_features_kind == "daily":
            month_sin, month_cos = cls.sin_cos(timestamp.month - 1, 12.0)
            day_sin, day_cos = cls.sin_cos(timestamp.day - 1, 31.0)
            week_sin, week_cos = cls.sin_cos(timestamp.isocalendar().week - 1, 53.0)
            quarter_sin, quarter_cos = cls.sin_cos((timestamp.month - 1) // 3, 4.0)
            return [
                month_sin,
                month_cos,
                day_sin,
                day_cos,
                week_sin,
                week_cos,
                quarter_sin,
                quarter_cos,
            ]
        return []

    @staticmethod
    def normalize_minmax(value: float, *, min_value: float, max_value: float) -> float:
        span = max_value - min_value
        if span <= 0:
            return 0.0
        return float((value - min_value) / span)

    @staticmethod
    def one_hot_encode(values_by_col: dict[str, str], one_hot_map: dict[str, list[str]]) -> list[float]:
        encoded: list[float] = []
        for col, categories in one_hot_map.items():
            current = str(values_by_col.get(col, ""))
            for category in categories:
                encoded.append(1.0 if current == category else 0.0)
        return encoded
