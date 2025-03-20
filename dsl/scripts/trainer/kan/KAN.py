from torch import nn

from kan.KAL import KAL
from kan.ParametricSigmoid import ParametricSigmoid

class KAN(nn.Module):
	def __init__(self, input_features, output_features):
		super().__init__()
		self.kal0 = KAL(input_features, 50)
		self.kal1 = KAL(50, output_features)
		self.sigmoid = nn.Sigmoid()

	def forward(self, x):
		x = self.kal0(x)
		x = self.kal1(x)
		return self.sigmoid(x)