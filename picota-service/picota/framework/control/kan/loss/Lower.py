import torch

from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind


class Lower(MetamorphicRelation):
    def __init__(self, margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.LOWER

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        return torch.relu(transformed_prediction - (base_prediction - self.margin)).mean()


__all__ = ["Lower"]
