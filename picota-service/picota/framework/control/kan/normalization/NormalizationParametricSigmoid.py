import torch
from torch import nn

from picota.framework.control.Device import Device
from picota.framework.control.kan.ParametricSigmoid import ParametricSigmoid


class NormalizationParametricSigmoid(nn.Module):
    eps = 0.01

    def __init__(self, mean, std):
        super().__init__()
        self.mean = mean
        self.std = nn.Parameter(torch.tensor([max(std, self.eps)], device=Device.getDevice()))
        self.sigmoid = ParametricSigmoid()

    def forward(self, x):
        z = (x - self.mean) / self.std
        z = torch.clamp(z, min=-3.0, max=3.0)
        return self.sigmoid.forward(z)


__all__ = ["NormalizationParametricSigmoid"]
