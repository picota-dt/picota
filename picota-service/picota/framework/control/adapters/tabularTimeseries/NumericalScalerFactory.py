from __future__ import annotations

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.MinMaxScalerStrategy import MinMaxScalerStrategy
from picota.framework.control.adapters.tabularTimeseries.NumericalScalerStrategy import NumericalScalerStrategy
from picota.framework.control.adapters.tabularTimeseries.PassthroughScalerStrategy import PassthroughScalerStrategy
from picota.framework.control.adapters.tabularTimeseries.ScalerBundle import ScalerBundle
from picota.framework.control.adapters.tabularTimeseries.ZScoreScalerStrategy import ZScoreScalerStrategy


class NumericalScalerFactory:
    def __init__(self):
        self.strategies: dict[str, NumericalScalerStrategy] = {
            "zscore": ZScoreScalerStrategy(),
            "minmax": MinMaxScalerStrategy(),
            "none": PassthroughScalerStrategy(),
        }

    def build(self, *, rows: list[ExampleRow], numerical_columns: list[str], scaler_kind: str) -> ScalerBundle:
        strategy = self.strategies.get(scaler_kind)
        if strategy is None:
            raise ValueError(f"Unsupported numerical scaler '{scaler_kind}'")
        return strategy.build_bundle(rows=rows, numerical_columns=numerical_columns)
