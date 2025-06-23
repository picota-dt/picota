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
        t = batch['t']
        t_features = batch['numerical_t_features']
        categorical_t_features = batch['categorical_t_features']
        lookback_t = batch['lookback_t']
        lookback_features = batch['numerical_lookback_features']
        categorical_lookback_features = batch['categorical_lookback_features']
        normalized_numerical_t_features = self.normalization_layer(t_features)
        if len(lookback_features) != 0 and lookback_features.size(1) != 0:
            normalized_lookback_features = self.normalization_layer(lookback_features)
            numerical_lookback_features_flat = normalized_lookback_features.view(normalized_lookback_features.size(0),
                                                                                 -1)
        else:
            numerical_lookback_features_flat = lookback_features
        if t.dim() == 1:
            t_1d = t
        else:
            t_1d = t.view(-1)
        if lookback_t.dim() == 1:
            lookback_t_reshaped = lookback_t
        else:
            lookback_t_reshaped = lookback_t.view(-1)
        if len(lookback_t) != 0 and lookback_t.size(1) != 0:
            lookback_t_flat = lookback_t_reshaped
        else:
            lookback_t_flat = torch.zeros((0,), device=t.device)
        if len(categorical_lookback_features) != 0 and categorical_lookback_features.size(1) != 0:
            categorical_lookback_features_flat = categorical_lookback_features.view(
                categorical_lookback_features.size(0))
        else:
            categorical_lookback_features_flat = torch.zeros((categorical_t_features.size(0),), device=t.device)
        x = torch.cat([
            t_1d.view(-1),
            normalized_numerical_t_features,
            categorical_t_features,
            lookback_t_flat,
            numerical_lookback_features_flat.view(-1),
            categorical_lookback_features_flat.view(-1),
        ])
        return x

    def output(self, x):
        return self.kan(x)
