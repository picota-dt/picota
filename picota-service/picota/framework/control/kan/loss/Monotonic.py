import torch

from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind


class Monotonic(MetamorphicRelation):
    def __init__(self, direction: str = "increasing", margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        if direction not in ("increasing", "decreasing"):
            raise ValueError("direction must be 'increasing' or 'decreasing'")
        self.direction = direction
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.MONOTONIC

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        if self.direction == "increasing":
            return torch.relu((base_prediction + self.margin) - transformed_prediction).mean()
        return torch.relu(transformed_prediction - (base_prediction - self.margin)).mean()


__all__ = ["Monotonic"]
