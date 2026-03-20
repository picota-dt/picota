from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import torch

Batch = dict[str, torch.Tensor]
BatchTransform = Callable[[Batch], Batch]
TargetTransform = Callable[..., torch.Tensor]


@dataclass(frozen=True)
class MetamorphicTransform:
    transform: BatchTransform
    target_transform: TargetTransform | None = None
    name: str | None = None
    weight: float = 1.0

    def __post_init__(self):
        if self.weight < 0:
            raise ValueError("MetamorphicTransform.weight must be >= 0")


__all__ = ["MetamorphicTransform"]
