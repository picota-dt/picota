from __future__ import annotations

from dataclasses import dataclass

from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicTransform import BatchTransform, TargetTransform


@dataclass(frozen=True)
class MetamorphicTest:
    relation: MetamorphicRelation
    transform: BatchTransform
    name: str | None = None
    target_transform: TargetTransform | None = None
    violation_atol: float | None = None
    violation_rtol: float | None = None

    def __post_init__(self):
        if self.target_transform is not None:
            raise ValueError(
                "Relation constraints cannot define target_transform. "
                "Use a target-mapped over_T MetamorphicTransform instead."
            )


__all__ = ["MetamorphicTest"]
