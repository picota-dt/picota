from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow


@dataclass(frozen=True)
class ScalerBundle:
    value_getter: Callable[[ExampleRow, str], float]
    means: list[float]
    stds: list[float]
    stats: dict[str, Any]
