import torch

def eval(model_path, inputs):
    arch = KAN(len(inputs), 1)
    arch.load_state_dict(torch.load(model_path, map_location=torch.device('cpu')))
    arch.eval()
    with torch.no_grad():
        return arch(inputs)