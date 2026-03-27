from __future__ import annotations

import numpy as np

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.ScalerBundle import ScalerBundle


class ZScoreScalerStrategy:
    def build_bundle(self, *, rows: list[ExampleRow], numerical_columns: list[str]) -> ScalerBundle:
        matrix = np.asarray(
            [[float(row.numerical_values[col]) for col in numerical_columns] for row in rows],
            dtype=np.float64,
        )
        means_array = matrix.mean(axis=0)
        stds_array = matrix.std(axis=0)
        stds_array = np.where(stds_array <= 1e-12, 1.0, stds_array)
        means = means_array.astype(np.float32).tolist()
        stds = stds_array.astype(np.float32).tolist()

        def getter(row: ExampleRow, col: str) -> float:
            return float(row.numerical_values[col])

        stats = {
            "means": {col: float(means[idx]) for idx, col in enumerate(numerical_columns)},
            "stds": {col: float(stds[idx]) for idx, col in enumerate(numerical_columns)},
        }
        return ScalerBundle(value_getter=getter, means=means, stds=stds, stats=stats)
