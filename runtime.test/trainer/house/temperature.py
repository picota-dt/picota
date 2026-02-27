import os

import torch
from torch import nn

import Device
from kan.KanTrainer import KanTrainer
from kan.MetamorphicCatalog import build_house_temperature_rule_specs
from kan.MetamorphicLoss import CompositeMetamorphicLoss
from kan.TimeSeriesDataset import TimeSeriesDataset


def input_variables(dict, output):
    return {key: valor for key, valor in dict.items() if key != output}


def train(subject, datasource, input_variables, means, stds, out_min, out_max, model_dir):
    model_path = model_dir + "/Temperature.bin"
    os.makedirs(model_dir, exist_ok=True)
    output_variable = "Temperature"
    device = Device.get_device()
    batch_size = 32
    epochs = 50
    lr = 0.0001
    sample = datasource[0] if datasource else None
    t_count = len(sample.get("t", [])) if sample else 0
    numerical_count = len(sample.get("numerical_t_features", [])) if sample else len(means)
    categorical_count = len(sample.get("categorical_t_features", [])) if sample else 0
    numerical_t_feature_names = list(input_variables)[t_count:t_count + numerical_count]
    rule_specs = build_house_temperature_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        categorical_t_feature_count=categorical_count,
        include_target_mapped=False,
    )
    loss_fn = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_loss=nn.MSELoss(),
        supervised_weight=1.0,
        relation_constraint_weight=0.25,
        worst_case_over_T_weight=1.0,
        target_mapped_weight=0.0,
    )


validation_loss_fn = nn.L1Loss()
test_proportion = 0.3
dataset = TimeSeriesDataset(datasource)
model, loss, margin_of_error, features = (
    KanTrainer(subject, input_variables, output_variable, 0, means, stds, out_min, out_max,
               batch_size, epochs, device, test_proportion, lr, loss_fn, validation_loss_fn)
    .train(dataset))
torch.save(model.state_dict(), model_path)
print(
    f"{subject}	{output_variable}	{loss}	{margin_of_error}	" +
    ",".join(f"{k}###{float(v):.2f}" for k, v in sorted(features.items(), key=lambda x: float(x[1]), reverse=True)),
    flush=True
)
