import torch

import Device
from kan.KAN import KAN
from kan.TimeSeriesDataset import TimeSeriesDataset


def eval(model_path, data, inputs, means, stds, lookback_size):
    arch = KAN(len(inputs), lookback_size, means, stds, 1)
    arch.load_state_dict(torch.load(model_path, map_location=Device.get_device(), weights_only=True))
    arch.to(Device.get_device())
    arch.eval()
    with torch.no_grad():
        return arch(TimeSeriesDataset(data)[0]).item()
