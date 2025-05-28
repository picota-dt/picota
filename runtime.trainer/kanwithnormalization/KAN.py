import torch

from torch import nn
from kanwithnormalization.KAL import KAL
from kanwithnormalization.NormalizationLayer import NormalizationLayer
from kanwithnormalization.ParametricSigmoid import ParametricSigmoid


class KAN(nn.Module):
    # length_of_input_features = (len(dataset[0]["now_conditions"]) +  len(dataset[0]["now_time"])) +  * (window_size + 1)
    # mean y std de cada feature.
    def __init__(self, input_features, means, stds, output_features):
        super().__init__()
        if len(means) != len(stds): raise ValueError('means and stds must have same length')
        self.kal0 = KAL(input_features, 50)
        self.kal1 = KAL(50, output_features)
        self.normalization_layer = NormalizationLayer(means, stds)
        self.sigmoid = ParametricSigmoid()

    def forward(self, batch):
        t_features = batch['t_features']
        now_time = batch['t']
        lookback_features = batch['lookback_features']
        lookback_t = batch['lookback_t']
        normalized_now_conditions = self.normalization_layer(t_features)
        if len(lookback_features[0]) != 0:
            normalized_context_conditions = self.normalization_layer(lookback_features)
        else:
            normalized_context_conditions = lookback_features
        context_conditions_flat = normalized_context_conditions.view(normalized_context_conditions.size(0), -1)
        context_time_flat = lookback_t.view(lookback_t.size(0), -1)
        x = torch.cat([
            normalized_now_conditions,
            context_conditions_flat,
            now_time,
            context_time_flat
        ], dim=1)
        x = self.kal0(x)
        x = self.kal1(x)
        result = self.sigmoid(x)
        return result
