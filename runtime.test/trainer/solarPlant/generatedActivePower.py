import os

import torch
from torch import nn

import trainer.Device as Device
from trainer.kan.KanTrainer import KanTrainer
from trainer.kan.TimeSeriesDataset import TimeSeriesDataset


def input_variables(dict, output):
    return {key: valor for key, valor in dict.items() if key != output}


def train(subject, datasource, input_variables, means, stds, out_min, out_max, model_dir):
    model_path = model_dir + "/generatedActivePower+6.bin"
    os.makedirs(model_dir, exist_ok=True)
    output_variable = "generatedActivePower+6"
    device = Device.get_device()
    batch_size = 32
    epochs = 50
    lr = 0.0001
    loss_fn = nn.MSELoss()
    validation_loss_fn = nn.L1Loss()
    test_proportion = 0.3
    dataset = TimeSeriesDataset(datasource)
    model, loss, margin_of_error, features = (
        KanTrainer(subject, input_variables, output_variable, 1, means, stds, out_min, out_max,
                   batch_size, epochs, device, test_proportion, lr, loss_fn, validation_loss_fn)
        .train(dataset))
    torch.save(model.state_dict(), model_path)
    print(
        f"{subject}	{output_variable}	{loss}	{margin_of_error}	" +
        ",".join(f"{k}###{float(v):.2f}" for k, v in sorted(features.items(), key=lambda x: float(x[1]), reverse=True)),
        flush=True
    )
