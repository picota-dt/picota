import os

import Device
import torch
from kan.KanTrainer import KanTrainer
from kan.TimeSeriesDataset import TimeSeriesDataset
from torch import nn


def input_variables(dict, output):
    return {key: valor for key, valor in dict.items() if key != output}


def train(subject, datasource, input_variables, means, stds, out_min, out_max, model_dir):
    model_path = model_dir + "/generatedReactivePower+6.bin"
    os.makedirs(model_dir, exist_ok=True)
    output_variable = "generatedReactivePower+6"
    device = Device.get_device()
    batch_size = 32
    epochs = 50
    lr = 0.0001
    loss_fn = nn.MSELoss()
    validation_loss_fn = nn.L1Loss()
    test_proportion = 0.3
    dataset = TimeSeriesDataset(datasource)
    trainer = KanTrainer(
        subject,
        input_variables,
        output_variable,
        0,
        means,
        stds,
        out_min,
        out_max,
        batch_size,
        epochs,
        device,
        test_proportion,
        lr,
        loss_fn,
        validation_loss_fn,
    )
    model, loss, margin_of_error, features = trainer.train(dataset)
    torch.save(model.state_dict(), model_path)
    val = trainer.last_validation_metrics or {}
    mae_model = float(val.get("mae_model", loss))
    rmse_model = float(val.get("rmse_model", float("nan")))
    r2 = float(val.get("r2", float("nan")))
    mae_raw = float(val.get("mae_raw", float("nan")))
    rmse_raw = float(val.get("rmse_raw", float("nan")))
    margin_model = float(val.get("margin_model", margin_of_error))
    margin_raw = float(val.get("margin_raw", float("nan")))
    output_scale = str(val.get("output_scale", "unknown"))
    n_samples = int(val.get("n_samples", 0))
    print(
        f"{subject}	{output_variable}	"
        f"val_n={n_samples}	"
        f"output_scale={output_scale}	"
        f"val_mae_model={mae_model:.6f}	"
        f"val_rmse_model={rmse_model:.6f}	"
        f"val_r2={r2:.6f}	"
        f"val_mae_raw={mae_raw:.6f}	"
        f"val_rmse_raw={rmse_raw:.6f}	"
        f"val_margin_model={margin_model:.6f}	"
        f"val_margin_raw={margin_raw:.6f}	"
        +
        ",".join(f"{k}###{float(v):.2f}" for k, v in sorted(features.items(), key=lambda x: float(x[1]), reverse=True)),
        flush=True
    )
