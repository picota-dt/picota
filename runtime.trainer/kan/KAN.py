from torch import nn

from trainer.kan.KAL import KAL
from trainer.kan.ParametricSigmoid import ParametricSigmoid


class KAN(nn.Module):
    def __init__(self, input_features, output_features):
        super().__init__()
        self.kal0 = KAL(input_features, 50)
        self.kal1 = KAL(50, 250)
        self.kal2 = KAL(250, 50)
        self.kal3 = KAL(50, output_features)
        self.sigmoid = ParametricSigmoid()

    def forward(self, x):
        x = self.kal0(x)
        x = self.kal1(x)
        x = self.kal2(x)
        x = self.kal3(x)
        return self.sigmoid(x)
