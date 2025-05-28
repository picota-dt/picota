import torch
from torch.utils.data import Dataset

import Device


class InputDataset(Dataset):
    def __init__(self, dataframe, output_var):
        self.input_data = self._convert_to_dataset(dataframe, output_var)
        self.output_data = dataframe[output_var]
        self.length = len(next(iter(dataframe.values())))
        self.device = Device.get_device()

    def __len__(self):
        return self.length

    def __getitem__(self, idx):
        return torch.tensor(self.input_data[idx], device=self.device), torch.tensor([self.output_data[idx]], device=self.device)

    def _convert_to_dataset(self, dataframe: dict, output_column):
        dataframe_copy = dataframe.copy()
        del dataframe_copy[output_column]
        return list(map(list, zip(*dataframe_copy.values())))
