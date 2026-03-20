from __future__ import annotations

from typing import Iterable

from picota.framework.control.kan.loss.MetamorphicTransform import MetamorphicTransform


class TransformSet:
    def __init__(self, transforms: Iterable[MetamorphicTransform] | None = None):
        self.transforms = tuple(transforms or ())

    def __iter__(self):
        return iter(self.transforms)

    def __len__(self) -> int:
        return len(self.transforms)

    def __bool__(self) -> bool:
        return len(self.transforms) > 0


__all__ = ["TransformSet"]
