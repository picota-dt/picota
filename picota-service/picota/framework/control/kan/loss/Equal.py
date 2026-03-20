import torch

from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind


class Equal(MetamorphicRelation):
    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.EQUAL

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        return torch.mean(torch.abs(transformed_prediction - base_prediction))


__all__ = ["Equal"]
