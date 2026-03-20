import torch

from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind


class Proportional(MetamorphicRelation):
    def __init__(
            self,
            factor: float,
            weight: float = 1.0,
            eps: float = 1e-8,
            raw_out_min: float | None = None,
            raw_out_max: float | None = None,
    ):
        super().__init__(weight=weight)
        self.factor = factor
        self.eps = eps
        self.raw_out_min = float(raw_out_min) if raw_out_min is not None else None
        self.raw_out_max = float(raw_out_max) if raw_out_max is not None else None

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.PROPORTIONAL

    def _to_relation_space(self, prediction: torch.Tensor) -> torch.Tensor:
        if self.raw_out_min is None or self.raw_out_max is None or not self.raw_out_max > self.raw_out_min:
            return prediction
        span = self.raw_out_max - self.raw_out_min
        return prediction * span + self.raw_out_min

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        base_relation = self._to_relation_space(base_prediction)
        transformed_relation = self._to_relation_space(transformed_prediction)
        expected = base_relation * self.factor
        scale = torch.clamp(torch.abs(expected), min=self.eps)
        return torch.mean(torch.abs(transformed_relation - expected) / scale)


__all__ = ["Proportional"]
