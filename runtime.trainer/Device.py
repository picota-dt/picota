import torch
import platform

def get_device():
    system = platform.system()
    if torch.cuda.is_available():
        return torch.device("cuda")
    elif system == "Darwin" and torch.backends.mps.is_available():
        return torch.device("mps")
    else:
        return torch.device("cpu")