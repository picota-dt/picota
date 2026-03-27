from __future__ import annotations

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.ScalerBundle import ScalerBundle


class MinMaxScalerStrategy:
    def build_bundle(self, *, rows: list[ExampleRow], numerical_columns: list[str]) -> ScalerBundle:
        mins: dict[str, float] = {}
        maxs: dict[str, float] = {}
        for col in numerical_columns:
            values = [float(row.numerical_values[col]) for row in rows]
            col_min = float(min(values))
            col_max = float(max(values))
            if col_max <= col_min:
                col_max = col_min + 1.0
            mins[col] = col_min
            maxs[col] = col_max

        def getter(row: ExampleRow, col: str) -> float:
            span = maxs[col] - mins[col]
            if span <= 0:
                return 0.0
            return float((float(row.numerical_values[col]) - mins[col]) / span)

        stats = {"mins": dict(mins), "maxs": dict(maxs)}
        return ScalerBundle(
            value_getter=getter,
            means=[0.0 for _ in numerical_columns],
            stds=[1.0 for _ in numerical_columns],
            stats=stats,
        )
