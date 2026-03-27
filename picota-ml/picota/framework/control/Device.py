import platform

import torch


class Device:
    @staticmethod
    def getDevice():
        system = platform.system()
        if torch.cuda.is_available():
            return torch.device("cuda")
        if system == "Darwin" and torch.backends.mps.is_available():
            return torch.device("mps")
        return torch.device("cpu")

    @staticmethod
    def get_device():
        return Device.getDevice()
