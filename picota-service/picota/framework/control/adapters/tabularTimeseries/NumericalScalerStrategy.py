from __future__ import annotations

from typing import Protocol

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.ScalerBundle import ScalerBundle


class NumericalScalerStrategy(Protocol):
    def build_bundle(self, *, rows: list[ExampleRow], numerical_columns: list[str]) -> ScalerBundle:
        ...
