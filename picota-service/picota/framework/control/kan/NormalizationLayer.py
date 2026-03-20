from picota.framework.control.kan.normalization.NormalizationLayer import NormalizationLayer as _NormalizationLayer
from picota.framework.control.kan.normalization.NormalizationParametricSigmoid import NormalizationParametricSigmoid


class NormalizationLayer(_NormalizationLayer):
    pass


__all__ = ["NormalizationParametricSigmoid", "NormalizationLayer"]
