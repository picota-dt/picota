from abc import ABC, abstractmethod

import torch

from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind


class MetamorphicRelation(ABC):
    def __init__(self, weight: float = 1.0):
        if weight < 0:
            raise ValueError("weight must be >= 0")
        self.weight = weight

    @property
    @abstractmethod
    def kind(self) -> MetamorphicRelationKind:
        raise NotImplementedError

    @abstractmethod
    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        raise NotImplementedError


__all__ = ["MetamorphicRelation"]
