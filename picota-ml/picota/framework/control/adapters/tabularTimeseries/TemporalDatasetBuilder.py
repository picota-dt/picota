from __future__ import annotations

import calendar
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from typing import Callable

from picota.framework.control.adapters.tabularTimeseries.AdapterOptions import AdapterOptions
from picota.framework.control.adapters.tabularTimeseries.AggregatedRow import AggregatedRow
from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow


class TemporalDatasetBuilder:
    _SUPPORTED_TIME_BUCKETS = {"years", "months", "days", "hours", "minutes", "seconds"}
    _TIME_BUCKET_ALIASES = {
        "year": "years",
        "month": "months",
        "day": "days",
        "hour": "hours",
        "minute": "minutes",
        "second": "seconds",
    }

    def __init__(self, *, options: AdapterOptions, numerical_columns: list[str], categorical_columns: list[str]):
        self.options = options
        self.numerical_columns = numerical_columns
        self.categorical_columns = categorical_columns

    def aggregate_rows(self, rows: list[dict[str, str]]) -> list[AggregatedRow]:
        grouped_target: dict[tuple[datetime, tuple[str, ...]], float] = defaultdict(float)
        grouped_count: dict[tuple[datetime, tuple[str, ...]], int] = defaultdict(int)
        grouped_numerical: dict[tuple[datetime, tuple[str, ...]], dict[str, float]] = defaultdict(
            lambda: defaultdict(float)
        )
        grouped_categorical: dict[tuple[datetime, tuple[str, ...]], dict[str, Counter[str]]] = defaultdict(
            lambda: defaultdict(Counter)
        )

        valid_rows = 0
        for row in rows:
            try:
                timestamp = self._parse_utc_timestamp(str(row[self.options.timestamp_column]))
                bucketed_timestamp = self._bucket_timestamp(timestamp, bucket=self.options.time_bucket)
                entity = self._entity_key(row, self.options.entity_key_columns)
                target_value = float(str(row[self.options.target_column]).strip())
                numerical_values = {col: float(str(row.get(col, "")).strip()) for col in self.numerical_columns}
            except (KeyError, TypeError, ValueError):
                continue

            group_key = (bucketed_timestamp, entity)
            grouped_target[group_key] += target_value
            grouped_count[group_key] += 1
            for col, value in numerical_values.items():
                grouped_numerical[group_key][col] += value
            for col in self.categorical_columns:
                grouped_categorical[group_key][col][str(row.get(col, "")).strip()] += 1
            valid_rows += 1

        if valid_rows == 0:
            raise ValueError("No valid rows after timestamp/target/feature parsing")

        aggregated_rows: list[AggregatedRow] = []
        for key in sorted(grouped_count.keys()):
            count = grouped_count[key]
            timestamp, entity = key
            target_avg = grouped_target[key] / float(count)
            numerical_avg: dict[str, float] = {}
            for col in self.numerical_columns:
                numerical_avg[col] = grouped_numerical[key][col] / float(count)
            categorical_mode: dict[str, str] = {}
            for col in self.categorical_columns:
                counter = grouped_categorical[key][col]
                categorical_mode[col] = counter.most_common(1)[0][0] if counter else ""
            aggregated_rows.append(
                AggregatedRow(
                    instant=timestamp,
                    entity_key=entity,
                    numerical_values=numerical_avg,
                    categorical_values=categorical_mode,
                    target_value=float(target_avg),
                )
            )
        return aggregated_rows

    def build_horizon_examples(
            self,
            rows: list[AggregatedRow],
            *,
            horizon_timestamp_resolver: Callable[[datetime], datetime]
    ) -> list[ExampleRow]:
        by_entity_time: dict[tuple[tuple[str, ...], datetime], AggregatedRow] = {}
        for row in rows:
            by_entity_time[(row.entity_key, row.instant)] = row

        examples: list[ExampleRow] = []
        for row in rows:
            future = by_entity_time.get((row.entity_key, horizon_timestamp_resolver(row.instant)))
            if future is None:
                continue
            examples.append(
                ExampleRow(
                    instant=row.instant,
                    numerical_values=row.numerical_values,
                    categorical_values=row.categorical_values,
                    target_future=float(future.target_value),
                )
            )
        return examples

    @classmethod
    def resolve_horizon_delta(
            cls,
            *,
            value: int,
            unit: str,
            time_bucket: str
    ) -> Callable[[datetime], datetime]:
        steps = int(value)
        if steps <= 0:
            raise ValueError("time_horizon must be > 0")
        normalized_unit = str(unit).strip().lower()

        if normalized_unit == "seconds":
            delta = timedelta(seconds=steps)
            return lambda instant: instant + delta
        if normalized_unit == "minutes":
            delta = timedelta(minutes=steps)
            return lambda instant: instant + delta
        if normalized_unit == "hours":
            delta = timedelta(hours=steps)
            return lambda instant: instant + delta
        if normalized_unit == "days":
            delta = timedelta(days=steps)
            return lambda instant: instant + delta
        if normalized_unit == "months":
            return lambda instant: cls._add_months(instant, steps)
        if normalized_unit == "years":
            return lambda instant: cls._add_years(instant, steps)
        if normalized_unit == "steps":
            normalized_bucket = cls._normalize_bucket(time_bucket)
            return lambda instant: cls._advance_by_bucket_steps(instant, bucket=normalized_bucket, steps=steps)

        raise ValueError(f"Unsupported horizon unit '{unit}'")

    @staticmethod
    def _parse_utc_timestamp(value: str) -> datetime:
        text = value.strip()
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)

    @classmethod
    def _bucket_timestamp(cls, timestamp: datetime, *, bucket: str) -> datetime:
        normalized_bucket = cls._normalize_bucket(bucket)
        if normalized_bucket == "years":
            return timestamp.replace(month=1, day=1, hour=0, minute=0, second=0, microsecond=0)
        if normalized_bucket == "months":
            return timestamp.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        if normalized_bucket == "days":
            return timestamp.replace(hour=0, minute=0, second=0, microsecond=0)
        if normalized_bucket == "hours":
            return timestamp.replace(minute=0, second=0, microsecond=0)
        if normalized_bucket == "minutes":
            return timestamp.replace(second=0, microsecond=0)
        return timestamp.replace(microsecond=0)

    @staticmethod
    def _entity_key(row: dict[str, str], entity_key_columns: list[str]) -> tuple[str, ...]:
        values: list[str] = []
        for col in entity_key_columns:
            raw_value = str(row.get(col, "")).strip()
            try:
                values.append(f"{float(raw_value):.12g}")
            except ValueError:
                values.append(raw_value)
        return tuple(values)

    @classmethod
    def _normalize_bucket(cls, bucket: str | None) -> str:
        if bucket is None:
            raise ValueError("data_source.options.time_bucket is required")
        value = str(bucket).strip().lower()
        if value in cls._SUPPORTED_TIME_BUCKETS:
            return value
        alias = cls._TIME_BUCKET_ALIASES.get(value)
        if alias is not None:
            return alias
        raise ValueError(f"Unsupported time bucket '{bucket}'")

    @classmethod
    def _advance_by_bucket_steps(cls, instant: datetime, *, bucket: str, steps: int) -> datetime:
        if bucket == "seconds":
            return instant + timedelta(seconds=steps)
        if bucket == "minutes":
            return instant + timedelta(minutes=steps)
        if bucket == "hours":
            return instant + timedelta(hours=steps)
        if bucket == "days":
            return instant + timedelta(days=steps)
        if bucket == "months":
            return cls._add_months(instant, steps)
        return cls._add_years(instant, steps)

    @staticmethod
    def _add_months(instant: datetime, months: int) -> datetime:
        total_months = (instant.year * 12) + (instant.month - 1) + months
        year = total_months // 12
        month = (total_months % 12) + 1
        day = min(instant.day, TemporalDatasetBuilder._days_in_month(year, month))
        return instant.replace(year=year, month=month, day=day)

    @staticmethod
    def _add_years(instant: datetime, years: int) -> datetime:
        year = instant.year + years
        day = min(instant.day, TemporalDatasetBuilder._days_in_month(year, instant.month))
        return instant.replace(year=year, day=day)

    @staticmethod
    def _days_in_month(year: int, month: int) -> int:
        return calendar.monthrange(year, month)[1]
