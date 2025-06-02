import torch
from torch.utils.data import Dataset, DataLoader

import Device


class TimeSeriesDataset(Dataset):
    def __init__(self, data):
        self.data = data
        self.device = Device.get_device()

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        item = self.data[idx]
        return {
            'out': torch.tensor(item['out'], dtype=torch.float32).to(device=self.device),
            't_features': torch.tensor(item['t_features'], dtype=torch.float32).to(device=self.device),
            # size = length of input features
            't': torch.tensor(item['t'], dtype=torch.float32).to(device=self.device),  # size = time features
            'lookback_features': torch.tensor(item['lookback_features'], dtype=torch.float32).to(device=self.device),
            # size = window* length of features
            'lookback_t': torch.tensor(item['lookback_t'], dtype=torch.float32).to(device=self.device)
            # size = window* length of time features
        }
