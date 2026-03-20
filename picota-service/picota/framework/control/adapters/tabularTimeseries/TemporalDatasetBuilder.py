from __future__ import annotations

from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone

from picota.framework.control.adapters.tabularTimeseries.AdapterOptions import AdapterOptions
from picota.framework.control.adapters.tabularTimeseries.AggregatedRow import AggregatedRow
from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow


class TemporalDatasetBuilder:
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

    def build_horizon_examples(self, rows: list[AggregatedRow], *, horizon_delta: timedelta) -> list[ExampleRow]:
        by_entity_time: dict[tuple[tuple[str, ...], datetime], AggregatedRow] = {}
        for row in rows:
            by_entity_time[(row.entity_key, row.instant)] = row

        examples: list[ExampleRow] = []
        for row in rows:
            future = by_entity_time.get((row.entity_key, row.instant + horizon_delta))
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

    @staticmethod
    def resolve_horizon_delta(*, value: int, unit: str, time_bucket: str) -> timedelta:
        if unit == "hours":
            delta = timedelta(hours=int(value))
        elif unit == "days":
            delta = timedelta(days=int(value))
        elif unit == "steps":
            if time_bucket == "hour":
                delta = timedelta(hours=int(value))
            elif time_bucket == "day":
                delta = timedelta(days=int(value))
            else:
                raise ValueError(
                    "time_horizon.unit='steps' requires data_source.options.time_bucket='hour' or 'day'"
                )
        else:
            raise ValueError(f"Unsupported horizon unit '{unit}'")

        if delta.total_seconds() <= 0:
            raise ValueError("time_horizon must be > 0")
        if time_bucket == "day" and (delta.total_seconds() % (24 * 3600)) != 0:
            raise ValueError("For time_bucket='day', horizon must be a whole number of days")
        if time_bucket == "hour" and (delta.total_seconds() % 3600) != 0:
            raise ValueError("For time_bucket='hour', horizon must be a whole number of hours")
        return delta

    @staticmethod
    def _parse_utc_timestamp(value: str) -> datetime:
        text = value.strip()
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)

    @staticmethod
    def _bucket_timestamp(timestamp: datetime, *, bucket: str) -> datetime:
        if bucket == "none":
            return timestamp
        if bucket == "hour":
            return timestamp.replace(minute=0, second=0, microsecond=0)
        if bucket == "day":
            return timestamp.replace(hour=0, minute=0, second=0, microsecond=0)
        raise ValueError(f"Unsupported time bucket '{bucket}'")

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
