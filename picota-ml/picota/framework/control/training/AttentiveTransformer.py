from __future__ import annotations

import torch
from torch import nn


class AttentiveTransformer(nn.Module):
    def __init__(self, input_dim: int, output_dim: int):
        super().__init__()
        self.proj = nn.Linear(input_dim, output_dim)

    def forward(self, x: torch.Tensor, prior: torch.Tensor, temperature: float) -> torch.Tensor:
        logits = self.proj(x)
        return torch.softmax((logits * prior) / max(temperature, 1e-6), dim=-1)


__all__ = ["AttentiveTransformer"]
