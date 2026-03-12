import os
import random

import Device
import numpy as np
import torch
from kan.KanTrainer import KanTrainer
from kan.MetamorphicCatalog import summarize_rule_specs
from kan.MetamorphicLoss import CompositeMetamorphicLoss
from kan.TimeSeriesDataset import TimeSeriesDataset
from torch import nn

from trainer.solarPlant.metamorphic_rules import build_solar_plant_active_power_rule_specs

# Keep this in sync with compare_active_power_runner defaults for apples-to-apples runs.
TRAINING_CONFIG = {
    "batch_size": 32,
    "epochs": 20,
    "lr": 1e-4,
    "seed": 42,
    "test_proportion": 0.20,  # => train/val/test ~= 64/16/20 in KanTrainer split
    "supervised_weight": 1.0,
    "relation_constraint_weight": 0.25,
    "worst_case_over_T_weight": 1.0,
}

# Editable map: per-rule override used during composite training.
RULE_WEIGHT_MAP: dict[str, float] = {
    "radiation_up_implies_active_power_non_decreasing": 1.0,
    "cell_temperature_up_tends_to_reduce_efficiency": 0.3,
}


def input_variables(dict, output):
    return {key: valor for key, valor in dict.items() if key != output}


def _apply_rule_weight_map(rule_specs, rule_weight_map):
    effective_weights: dict[str, float] = {}
    active_rule_names: set[str] = set()
    for spec in rule_specs:
        spec_name = str(getattr(spec, "name", ""))
        if not spec_name:
            continue
        active_rule_names.add(spec_name)
        relation_test = getattr(spec, "relation_test", None)
        if relation_test is None:
            continue
        if spec_name in rule_weight_map:
            weight = float(rule_weight_map[spec_name])
            if weight < 0:
                raise ValueError(f"RULE_WEIGHT_MAP['{spec_name}'] must be >= 0")
            relation_test.relation.weight = weight
        effective_weights[spec_name] = float(relation_test.relation.weight)
    inactive_rules = sorted(set(rule_weight_map.keys()) - active_rule_names)
    return rule_specs, effective_weights, inactive_rules


def train(subject, datasource, input_variables, means, stds, out_min, out_max, model_dir):
    model_path = model_dir + "/generatedActivePower+6.bin"
    os.makedirs(model_dir, exist_ok=True)
    output_variable = "generatedActivePower+6"
    device = Device.get_device()
    batch_size = int(TRAINING_CONFIG["batch_size"])
    epochs = int(TRAINING_CONFIG["epochs"])
    lr = float(TRAINING_CONFIG["lr"])
    seed = int(TRAINING_CONFIG["seed"])
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    if hasattr(torch.backends, "cudnn"):
        torch.backends.cudnn.deterministic = True
        torch.backends.cudnn.benchmark = False
    numerical_t_feature_names = list(input_variables)[-len(means):]
    rule_specs = build_solar_plant_active_power_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        categorical_t_feature_count=0,
    )
    rule_specs, effective_rule_weights, inactive_rules = _apply_rule_weight_map(
        rule_specs=rule_specs,
        rule_weight_map=RULE_WEIGHT_MAP,
    )
    rule_summary = summarize_rule_specs(rule_specs)
    print(
        f"{subject}\t{output_variable}\t"
        f"train_config=batch_size:{batch_size},epochs:{epochs},lr:{lr},seed:{seed},"
        f"test_proportion:{TRAINING_CONFIG['test_proportion']},"
        f"supervised_weight:{TRAINING_CONFIG['supervised_weight']},"
        f"relation_constraint_weight:{TRAINING_CONFIG['relation_constraint_weight']},"
        f"worst_case_over_T_weight:{TRAINING_CONFIG['worst_case_over_T_weight']}",
        flush=True,
    )
    print(f"{subject}\t{output_variable}\trule_weight_map={effective_rule_weights}", flush=True)
    if inactive_rules:
        print(f"{subject}\t{output_variable}\trule_weight_map_inactive={inactive_rules}", flush=True)
    print(
        f"{subject}\t{output_variable}\t"
        f"catalog=specs:{rule_summary['num_specs']},"
        f"relation_tests:{rule_summary['num_relation_tests']},"
        f"over_T_transforms:{rule_summary['num_over_T_transforms']},"
        f"categories:{rule_summary['by_category']}",
        flush=True,
    )
    loss_fn = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_loss=nn.MSELoss(),
        supervised_weight=float(TRAINING_CONFIG["supervised_weight"]),
        relation_constraint_weight=float(TRAINING_CONFIG["relation_constraint_weight"]),
        worst_case_over_T_weight=float(TRAINING_CONFIG["worst_case_over_T_weight"]),
    )
    validation_loss_fn = nn.L1Loss()
    test_proportion = float(TRAINING_CONFIG["test_proportion"])
    dataset = TimeSeriesDataset(datasource)
    trainer = KanTrainer(
        subject,
        input_variables,
        output_variable,
        1,
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
