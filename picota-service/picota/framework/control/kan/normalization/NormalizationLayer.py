import torch
from torch import nn

from picota.framework.control.kan.normalization.NormalizationParametricSigmoid import (
    NormalizationParametricSigmoid,
)


class NormalizationLayer(nn.Module):
    def __init__(self, means: list[float], stds: list[float]):
        super().__init__()
        self.size = len(means)
        self.layers = nn.ModuleList(
            [NormalizationParametricSigmoid(means[i], stds[i]) for i in range(len(means))]
        )

    def forward(self, x):
        if x.dim() == 1:
            assert x.shape[0] == self.size
            outputs = [normalization_layers(x[i]) for i, normalization_layers in enumerate(self.layers)]
            return torch.stack(outputs).squeeze(1)
        if x.dim() == 2:
            assert x.shape[1] == self.size
            outputs = [layer(x[:, i]) for i, layer in enumerate(self.layers)]
            return torch.stack(outputs, dim=-1)
        if x.dim() == 3:
            assert x.shape[2] == self.size
            outputs = [layer(x[:, :, i]) for i, layer in enumerate(self.layers)]
            return torch.stack(outputs, dim=-1)
        raise ValueError(f"Unsupported input shape: {x.shape}")


__all__ = ["NormalizationLayer"]
