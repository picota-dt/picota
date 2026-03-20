from __future__ import annotations

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.ScalerBundle import ScalerBundle


class PassthroughScalerStrategy:
    def build_bundle(self, *, rows: list[ExampleRow], numerical_columns: list[str]) -> ScalerBundle:
        _ = rows

        def getter(row: ExampleRow, col: str) -> float:
            return float(row.numerical_values[col])

        return ScalerBundle(
            value_getter=getter,
            means=[0.0 for _ in numerical_columns],
            stds=[1.0 for _ in numerical_columns],
            stats={},
        )
