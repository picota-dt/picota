import torch

from torch import nn
from kan.KAL import KAL
from kan.NormalizationLayer import NormalizationLayer
from kan.ParametricSigmoid import ParametricSigmoid

class KAN(nn.Module):
#length_of_input_features = (len(dataset[0]["now_conditions"]) +  len(dataset[0]["now_time"])) +  * (window_size + 1)
# mean y std de cada feature.
    def __init__(self, input_features, means, stds, output_features):
        super().__init__()
        if len(means) != len(stds): raise ValueError('means and stds must have same length')
        self.kal0 = KAL(input_features, 50)
        self.kal1 = KAL(50, output_features)
        self.normalization_layer = NormalizationLayer(means, stds)
        self.sigmoid = ParametricSigmoid()

    def forward(self, x_now_conditions, x_now_time, x_context_conditions, x_context_time):
        normalized_now_conditions = self.normalization_layer(x_now_conditions)
        normalized_context_conditions = self.normalization_layer(x_context_conditions)
        context_conditions_flat = normalized_context_conditions.view(normalized_context_conditions.size(0), -1)
        context_time_flat = x_context_time.view(x_context_time.size(0), -1)
        x = torch.cat([
            normalized_now_conditions,
            context_conditions_flat,
            x_now_time,
            context_time_flat
        ], dim=1)
        x = self.kal0(x)
        x = self.kal1(x)
        result = self.sigmoid(x)
        return result