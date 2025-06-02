import torch

from torch import nn
from kan.KAL import KAL
from kan.NormalizationLayer import NormalizationLayer
from kan.ParametricSigmoid import ParametricSigmoid


class KAN(nn.Module):

    def __init__(self, input_features, lookback_size, means, stds, output_features):
        super().__init__()
        if len(means) != len(stds):
            raise ValueError('means and stds must have same length')
        self.normalization_layer = NormalizationLayer(means, stds)
        self.kan = self.build(input_features, lookback_size, output_features)

    def build(self, input_features, lookback_size, output_features):
        return nn.Sequential(
            KAL(input_features + lookback_size * input_features, 50),
            KAL(50, output_features),
            ParametricSigmoid()
        )

    def forward(self, batch):
        x = self.normalize(batch)
        return self.output(x)

    def normalize(self, batch):
        t_features = batch['t_features']
        now_time = batch['t']
        lookback_features = batch['lookback_features']
        lookback_t = batch['lookback_t']
        normalized_now_conditions = self.normalization_layer(t_features)
        if lookback_features.size(1) != 0:
            normalized_context_conditions = self.normalization_layer(lookback_features)
            context_conditions_flat = normalized_context_conditions.view(normalized_context_conditions.size(0), -1)
        else:
            context_conditions_flat = torch.zeros((t_features.size(0), 0), device=t_features.device)
        if now_time.dim() == 1:
            now_time = now_time.unsqueeze(1)
        if lookback_t.dim() == 1:
            lookback_t = lookback_t.unsqueeze(1)
        context_time_flat = lookback_t.view(lookback_t.size(0), -1)
        x = torch.cat([
            now_time,
            normalized_now_conditions,
            context_time_flat,
            context_conditions_flat
        ], dim=1)
        return x

    def output(self, x):
        return self.kan(x)
