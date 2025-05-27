import torch

from KAN import KAN

def eval(model_path, inputs):
    arch = KAN(len(inputs), 1)
    arch.load_state_dict(torch.load(model_path, map_location=torch.device('cpu'), weights_only=True))
    arch.eval()
    with torch.no_grad():
        return arch(inputs).item()