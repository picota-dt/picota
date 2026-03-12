import argparse
import copy
import json
import math
import random
import sys
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
PROJECT_ROOT = ROOT.parent

import Device
from kan.DatasetLoader import DatasetLoader
from kan.KAN import KAN
from kan.MetamorphicCatalog import summarize_rule_specs
from trainer.metamorphic_evaluation import (
    clone_batch,
    compute_violation_report,
    evaluate_worst_case_over_T,
    validate_metamorphic_transforms_on_batch,
    violation_mask,
)
from trainer.solarPlant.metamorphic_rules import build_solar_plant_active_power_rule_specs
from kan.MetamorphicLoss import (
    CompositeMetamorphicLoss,
    MetamorphicTest,
)
from kan.TimeSeriesDataset import TimeSeriesDataset

# Editable map: per-rule weight overrides.
# The final contribution of each rule is:
#   relation rules   -> relation_constraint_weight (global) * RULE_WEIGHT_MAP[rule]
#   over-T transforms -> worst_case_over_T_weight (global) * RULE_WEIGHT_MAP[rule]
RULE_WEIGHT_MAP: dict[str, float] = {
    # Directional / relation rules:
    "radiation_up_implies_active_power_non_decreasing": 1.0,
    "cell_temperature_up_tends_to_reduce_efficiency": 0.3,
}


@dataclass
class SplitData:
    train_items: list[dict]
    val_items: list[dict]
    test_items: list[dict]


@dataclass
class TrainingConfig:
    epochs: int
    batch_size: int
    lr: float
    seed: int


@dataclass
class ModelResult:
    model: KAN
    train_history: list[dict[str, float]]
    test_metrics: dict[str, float]
    violation_report: dict | None = None
    worst_case_over_T_report: dict | None = None


def find_relation_test_by_name(relation_tests: list[MetamorphicTest], rule_name: str) -> MetamorphicTest | None:
    for test in relation_tests:
        if (test.name or "") == rule_name:
            return test
    return None


def compute_single_rule_debug_stats(
        model,
        data_loader,
        relation_test: MetamorphicTest,
        max_batches: int | None = None,
) -> dict[str, float]:
    model.eval()
    total_violations = 0
    total_cases = 0
    delta_sum = 0.0
    delta_min = float("inf")
    delta_max = float("-inf")

    with torch.no_grad():
        for batch_idx, batch in enumerate(data_loader):
            if max_batches is not None and batch_idx >= max_batches:
                break

            base_pred = model(batch).squeeze().reshape(-1)
            transformed_batch = relation_test.transform(clone_batch(batch))
            transformed_pred = model(transformed_batch).squeeze().reshape(-1)

            local_atol = 1e-6 if getattr(relation_test, "violation_atol", None) is None else float(
                relation_test.violation_atol)
            local_rtol = 1e-4 if getattr(relation_test, "violation_rtol", None) is None else float(
                relation_test.violation_rtol)
            violations = violation_mask(
                relation_test.relation,
                base_pred,
                transformed_pred,
                atol=local_atol,
                rtol=local_rtol,
            )

            delta = transformed_pred - base_pred
            total_violations += int(violations.sum().item())
            total_cases += int(violations.numel())
            delta_sum += float(delta.sum().item())
            delta_min = min(delta_min, float(delta.min().item()))
            delta_max = max(delta_max, float(delta.max().item()))

    if total_cases == 0:
        return {
            "available": 0.0,
            "violation_rate": float("nan"),
            "violations": 0.0,
            "cases": 0.0,
            "mean_delta_pred": float("nan"),
            "min_delta_pred": float("nan"),
            "max_delta_pred": float("nan"),
        }

    return {
        "available": 1.0,
        "violation_rate": float(total_violations / total_cases),
        "violations": float(total_violations),
        "cases": float(total_cases),
        "mean_delta_pred": float(delta_sum / total_cases),
        "min_delta_pred": float(delta_min),
        "max_delta_pred": float(delta_max),
    }


def apply_rule_weight_map(rule_specs: list[object], rule_weight_map: dict[str, float]) -> tuple[
    list[object], dict[str, float], list[str]]:
    adjusted_specs: list[object] = []
    effective_weights: dict[str, float] = {}
    active_rule_names: list[str] = []

    for spec in rule_specs:
        spec_name = str(getattr(spec, "name", ""))
        if spec_name:
            active_rule_names.append(spec_name)
        relation_test = getattr(spec, "relation_test", None)
        over_t_transform = getattr(spec, "over_T_transform", None)

        if relation_test is not None:
            if spec_name in rule_weight_map:
                weight = float(rule_weight_map[spec_name])
                if weight < 0:
                    raise ValueError(f"RULE_WEIGHT_MAP['{spec_name}'] must be >= 0")
                relation_test.relation.weight = weight
            effective_weights[spec_name] = float(relation_test.relation.weight)
            adjusted_specs.append(spec)
            continue

        if over_t_transform is not None:
            if spec_name in rule_weight_map:
                weight = float(rule_weight_map[spec_name])
                if weight < 0:
                    raise ValueError(f"RULE_WEIGHT_MAP['{spec_name}'] must be >= 0")
                over_t_transform = replace(over_t_transform, weight=weight)
                spec = replace(spec, over_T_transform=over_t_transform)
            effective_weights[spec_name] = float(getattr(over_t_transform, "weight", 1.0))
            adjusted_specs.append(spec)
            continue

        adjusted_specs.append(spec)

    inactive_configured = sorted(set(rule_weight_map.keys()) - set(active_rule_names))
    return adjusted_specs, effective_weights, inactive_configured


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def split_items(items: list[dict], seed: int, train_ratio: float, val_ratio: float, test_ratio: float) -> SplitData:
    if not math.isclose(train_ratio + val_ratio + test_ratio, 1.0, rel_tol=1e-6, abs_tol=1e-6):
        raise ValueError("train_ratio + val_ratio + test_ratio must be 1.0")
    if len(items) < 3:
        raise ValueError("Need at least 3 items to create train/val/test split")

    rng = np.random.default_rng(seed)
    indices = np.arange(len(items))
    rng.shuffle(indices)

    train_end = max(1, int(len(items) * train_ratio))
    val_end = min(len(items) - 1, train_end + max(1, int(len(items) * val_ratio)))
    if val_end >= len(items):
        val_end = len(items) - 1
    if train_end >= val_end:
        train_end = max(1, val_end - 1)

    train_idx = indices[:train_end]
    val_idx = indices[train_end:val_end]
    test_idx = indices[val_end:]
    if len(test_idx) == 0:
        test_idx = indices[-1:]
        val_idx = indices[train_end:-1]
        if len(val_idx) == 0:
            val_idx = indices[train_end - 1:train_end]
            train_idx = indices[:train_end - 1]

    return SplitData(
        train_items=[items[int(i)] for i in train_idx],
        val_items=[items[int(i)] for i in val_idx],
        test_items=[items[int(i)] for i in test_idx],
    )


def make_loader(items: list[dict], batch_size: int, shuffle: bool) -> DataLoader:
    return DataLoader(TimeSeriesDataset(items), batch_size=batch_size, shuffle=shuffle)


def compute_loss(loss_fn, model, batch, target, prediction):
    if loss_fn is None:
        return torch.nn.functional.mse_loss(prediction, target)
    if hasattr(loss_fn, "compute_training_loss"):
        return loss_fn.compute_training_loss(model=model, batch=batch, target=target, prediction=prediction)
    return loss_fn(prediction, target)


def train_model(
        split: SplitData,
        input_variables: list[str],
        lookback: int,
        means: list[float],
        stds: list[float],
        device,
        loss_fn,
        config: TrainingConfig,
        run_name: str = "model",
        debug_rule_test: MetamorphicTest | None = None,
        debug_log_every: int = 1,
        debug_max_batches: int | None = 4,
) -> ModelResult:
    set_seed(config.seed)
    model = KAN(len(input_variables), lookback, means, stds, 1).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=config.lr)

    train_loader = make_loader(split.train_items, config.batch_size, shuffle=True)
    val_loader = make_loader(split.val_items, config.batch_size, shuffle=False)

    best_state = copy.deepcopy(model.state_dict())
    best_val_mae = float("inf")
    history: list[dict[str, float]] = []

    for epoch in range(config.epochs):
        model.train()
        total_loss = 0.0
        total_count = 0
        supervised_sum = 0.0
        relation_constraint_sum = 0.0
        worst_case_over_T_sum = 0.0
        target_mapped_sum = 0.0
        metric_count = 0

        for batch in train_loader:
            out = batch["out"]
            optimizer.zero_grad()
            pred = model(batch).squeeze()
            loss = compute_loss(loss_fn, model, batch, out, pred)
            loss.backward()
            optimizer.step()

            batch_size = out.shape[0] if out.dim() > 0 else 1
            total_loss += float(loss.detach().item()) * batch_size
            total_count += batch_size

            if loss_fn is not None and hasattr(loss_fn, "last_metrics") and loss_fn.last_metrics:
                supervised_sum += loss_fn.last_metrics.get("supervised_loss", 0.0) * batch_size
                relation_constraint_sum += loss_fn.last_metrics.get("relation_constraint_penalty", 0.0) * batch_size
                worst_case_over_T_sum += loss_fn.last_metrics.get("worst_case_over_T_loss", 0.0) * batch_size
                target_mapped_sum += loss_fn.last_metrics.get("target_mapped_supervised_loss", 0.0) * batch_size
                metric_count += batch_size

        val_metrics = evaluate_regression(model, val_loader, device)
        if val_metrics["mae"] < best_val_mae:
            best_val_mae = val_metrics["mae"]
            best_state = copy.deepcopy(model.state_dict())

        row = {
            "epoch": float(epoch + 1),
            "train_total_loss": total_loss / max(1, total_count),
            "val_mae": val_metrics["mae"],
        }
        if metric_count > 0:
            row["train_supervised_loss"] = supervised_sum / metric_count
            row["train_relation_constraint_penalty"] = relation_constraint_sum / metric_count
            row["train_worst_case_over_T_loss"] = worst_case_over_T_sum / metric_count
            row["train_target_mapped_supervised_loss"] = target_mapped_sum / metric_count

        if debug_rule_test is not None and debug_log_every > 0 and ((epoch + 1) % debug_log_every == 0):
            train_debug = compute_single_rule_debug_stats(
                model=model,
                data_loader=make_loader(split.train_items, config.batch_size, shuffle=False),
                relation_test=debug_rule_test,
                max_batches=debug_max_batches,
            )
            val_debug = compute_single_rule_debug_stats(
                model=model,
                data_loader=val_loader,
                relation_test=debug_rule_test,
                max_batches=debug_max_batches,
            )
            row["debug_train_rule_violation_rate"] = train_debug["violation_rate"]
            row["debug_val_rule_violation_rate"] = val_debug["violation_rate"]
            row["debug_train_rule_mean_delta_pred"] = train_debug["mean_delta_pred"]
            row["debug_val_rule_mean_delta_pred"] = val_debug["mean_delta_pred"]
            print(
                f"[rule_debug:{run_name}] "
                f"epoch={epoch + 1}/{config.epochs} "
                f"rule={debug_rule_test.name} "
                f"train_violation={train_debug['violation_rate']:.6f}"
                f" ({int(train_debug['violations'])}/{int(train_debug['cases'])}) "
                f"val_violation={val_debug['violation_rate']:.6f}"
                f" ({int(val_debug['violations'])}/{int(val_debug['cases'])}) "
                f"train_delta_mean={train_debug['mean_delta_pred']:.6f} "
                f"val_delta_mean={val_debug['mean_delta_pred']:.6f} "
                f"train_delta_range=[{train_debug['min_delta_pred']:.6f},{train_debug['max_delta_pred']:.6f}] "
                f"val_delta_range=[{val_debug['min_delta_pred']:.6f},{val_debug['max_delta_pred']:.6f}]"
            )
        history.append(row)

    model.load_state_dict(best_state)
    test_loader = make_loader(split.test_items, config.batch_size, shuffle=False)
    test_metrics = evaluate_regression(model, test_loader, device)
    return ModelResult(model=model, train_history=history, test_metrics=test_metrics)


def evaluate_regression(model, data_loader, device, tolerance: float | None = None) -> dict[str, float]:
    model.eval()
    preds = []
    targets = []
    with torch.no_grad():
        for batch in data_loader:
            pred = model(batch).squeeze()
            out = batch["out"]
            preds.append(pred.detach().to(device=device).reshape(-1))
            targets.append(out.detach().to(device=device).reshape(-1))

    if not preds:
        return {"mae": float("nan"), "rmse": float("nan"), "r2": float("nan")}

    y_pred = torch.cat(preds)
    y_true = torch.cat(targets)
    err = y_pred - y_true
    mae = float(torch.mean(torch.abs(err)).item())
    rmse = float(torch.sqrt(torch.mean(err ** 2)).item())

    ss_res = torch.sum(err ** 2)
    ss_tot = torch.sum((y_true - torch.mean(y_true)) ** 2)
    if float(ss_tot.item()) == 0.0:
        r2 = float("nan")
    else:
        r2 = float((1.0 - ss_res / ss_tot).item())

    metrics = {"mae": mae, "rmse": rmse, "r2": r2}
    if tolerance is not None:
        metrics["acc@tol"] = float((torch.abs(err) <= tolerance).float().mean().item())
    return metrics


def infer_output_scale(items: list[dict], out_min: float | None, out_max: float | None) -> str:
    if not items:
        return "normalized"
    values = [float(item["out"]) for item in items if "out" in item]
    if not values:
        return "normalized"
    observed_min = min(values)
    observed_max = max(values)
    observed_span = max(0.0, observed_max - observed_min)
    if -0.05 <= observed_min <= 1.05 and -0.05 <= observed_max <= 1.05:
        return "normalized"
    if out_min is not None and out_max is not None:
        raw_span = float(out_max) - float(out_min)
        if raw_span > 0:
            if observed_span <= 1.5 and raw_span > 1.5:
                return "normalized"
            if abs(observed_span - raw_span) <= 0.10 * max(raw_span, 1e-9):
                return "raw"
    return "raw"


def resolve_acc_tolerance(
        explicit_tolerance: float | None,
        explicit_tolerance_scale: str,
        default_ratio: float,
        output_scale: str,
        eval_items: list[dict],
        out_min: float | None,
        out_max: float | None,
) -> tuple[float | None, dict]:
    if default_ratio < 0:
        raise ValueError("--acc-tolerance-ratio must be >= 0")

    raw_span = None
    if out_min is not None and out_max is not None:
        candidate = float(out_max) - float(out_min)
        if candidate > 0:
            raw_span = candidate

    observed_values = [float(item["out"]) for item in eval_items if "out" in item]
    observed_model_span = None
    if observed_values:
        observed_model_span = max(0.0, max(observed_values) - min(observed_values))

    def to_model_units(value: float, value_scale: str) -> float:
        if value_scale == output_scale:
            return value
        if raw_span is None:
            raise ValueError(
                "Cannot convert acc@tol between raw and normalized scales without valid out_min/out_max metadata"
            )
        if value_scale == "raw" and output_scale == "normalized":
            return value / raw_span
        if value_scale == "normalized" and output_scale == "raw":
            return value * raw_span
        raise ValueError(f"Unsupported scale conversion: {value_scale} -> {output_scale}")

    def to_raw_units(model_value: float) -> float | None:
        if raw_span is None:
            return None
        if output_scale == "raw":
            return model_value
        if output_scale == "normalized":
            return model_value * raw_span
        return None

    if explicit_tolerance is not None:
        tolerance_model = to_model_units(float(explicit_tolerance), explicit_tolerance_scale)
        source = f"explicit_{explicit_tolerance_scale}"
        input_value = float(explicit_tolerance)
        input_scale = explicit_tolerance_scale
    else:
        if raw_span is not None:
            default_scale = "raw"
            default_value = default_ratio * raw_span
            source = "default_ratio_of_raw_range"
        elif observed_model_span is not None:
            default_scale = output_scale
            default_value = default_ratio * observed_model_span
            source = f"default_ratio_of_observed_{output_scale}_range"
        else:
            return None, {
                "enabled": False,
                "reason": "no_targets",
                "output_scale": output_scale,
                "input_scale": explicit_tolerance_scale,
            }
        tolerance_model = to_model_units(default_value, default_scale)
        input_value = default_value
        input_scale = default_scale

    return tolerance_model, {
        "enabled": True,
        "source": source,
        "output_scale": output_scale,
        "input_scale": input_scale,
        "input_value": float(input_value),
        "model_value": float(tolerance_model),
        "raw_value": to_raw_units(float(tolerance_model)),
        "raw_span": raw_span,
        "observed_model_span": observed_model_span,
        "default_ratio": float(default_ratio),
    }


def load_headers(jsonl_path: Path, md_path: Path) -> tuple[dict, dict]:
    with jsonl_path.open("r", encoding="utf-8") as f_jsonl:
        jsonl_header = json.loads(f_jsonl.readline().strip())
    with md_path.open("r", encoding="utf-8") as f_md:
        md_header = json.loads(f_md.readline().strip())
    return jsonl_header, md_header


def print_report(title: str, result: ModelResult):
    print(f"\n[{title}]")
    if result.train_history:
        last = result.train_history[-1]
        print(
            "train(last): "
            f"loss={last.get('train_total_loss', float('nan')):.6f} "
            f"val_mae={last.get('val_mae', float('nan')):.6f} "
            f"supervised_loss={last.get('train_supervised_loss', 0.0):.6f} "
            f"relation_constraint_penalty={last.get('train_relation_constraint_penalty', 0.0):.6f} "
            f"worst_case_over_T_loss={last.get('train_worst_case_over_T_loss', 0.0):.6f} "
            f"target_mapped_supervised_loss={last.get('train_target_mapped_supervised_loss', 0.0):.6f}"
        )
    print(
        "test: "
        f"mae={result.test_metrics['mae']:.6f} "
        f"rmse={result.test_metrics['rmse']:.6f} "
        f"r2={result.test_metrics['r2']:.6f} "
        f"acc@tol={result.test_metrics.get('acc@tol', float('nan')):.6f}"
    )
    if result.violation_report is not None:
        report = result.violation_report
        print(
            "violation_rate: "
            f"overall={report['overall_violation_rate']:.6f} "
            f"({report['total_violations']}/{report['total_cases']})"
        )
        for test_name, stats in sorted(report["by_test"].items()):
            print(
                f"  - {test_name}: rate={stats['violation_rate']:.6f} "
                f"({stats['violations']}/{stats['total']})"
            )
    if result.worst_case_over_T_report is not None:
        report = result.worst_case_over_T_report
        if not report.get("available", False):
            print(
                "worst_case_over_T_eval: "
                f"not_available reason={report.get('reason')} "
                f"num_transforms={report.get('num_transforms', 0)}"
            )
        else:
            print(
                "worst_case_over_T_eval: "
                f"num_transforms={report['num_transforms']} "
                f"worst_mae={report['worst_case_mae_over_T']:.6f} "
                f"worst_rmse={report['worst_case_rmse_over_T']:.6f} "
                f"worst_loss={report['worst_case_loss_over_T']:.6f} "
                f"worst_acc@tol={report.get('worst_case_acc@tol_over_T', float('nan')):.6f}"
            )


def compute_delta_against_baseline(candidate: ModelResult, baseline: ModelResult, prefix: str) -> dict[str, float]:
    delta = {
        f"delta_mae_{prefix}_minus_base": candidate.test_metrics["mae"] - baseline.test_metrics["mae"],
        f"delta_rmse_{prefix}_minus_base": candidate.test_metrics["rmse"] - baseline.test_metrics["rmse"],
        f"delta_r2_{prefix}_minus_base": candidate.test_metrics["r2"] - baseline.test_metrics["r2"],
    }
    if "acc@tol" in baseline.test_metrics and "acc@tol" in candidate.test_metrics:
        delta[f"delta_acc@tol_{prefix}_minus_base"] = candidate.test_metrics["acc@tol"] - baseline.test_metrics[
            "acc@tol"]
    if baseline.worst_case_over_T_report and candidate.worst_case_over_T_report:
        if baseline.worst_case_over_T_report.get("available") and candidate.worst_case_over_T_report.get("available"):
            delta[f"delta_worst_case_mae_over_T_{prefix}_minus_base"] = (
                    candidate.worst_case_over_T_report["worst_case_mae_over_T"]
                    - baseline.worst_case_over_T_report["worst_case_mae_over_T"]
            )
            delta[f"delta_worst_case_rmse_over_T_{prefix}_minus_base"] = (
                    candidate.worst_case_over_T_report["worst_case_rmse_over_T"]
                    - baseline.worst_case_over_T_report["worst_case_rmse_over_T"]
            )
    return delta


def main():
    parser = argparse.ArgumentParser(
        description="Compare baseline vs composite metamorphic training on SolarPlant generatedActivePower."
    )
    parser.add_argument(
        "--jsonl",
        type=Path,
        default=PROJECT_ROOT / "temp" / "data" / "infecar" / "SolarPlant_generatedActivePower.jsonl",
        help="Path to SolarPlant_generatedActivePower.jsonl",
    )
    parser.add_argument(
        "--md",
        type=Path,
        default=PROJECT_ROOT / "temp" / "data" / "infecar" / "SolarPlant_generatedActivePower+6.md",
        help="Path to SolarPlant_generatedActivePower+6.md",
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--train-ratio", type=float, default=0.64)
    parser.add_argument("--val-ratio", type=float, default=0.16)
    parser.add_argument("--test-ratio", type=float, default=0.20)
    parser.add_argument(
        "--relation-constraint-weight",
        type=float,
        default=0.25,
        help="Weight for relation_constraint_penalty term in CompositeMetamorphicLoss",
    )
    parser.add_argument(
        "--supervised-weight",
        type=float,
        default=1.0,
        help="Weight for l(f(x), y) in CompositeMetamorphicLoss",
    )
    parser.add_argument(
        "--worst-case-over-T-weight",
        type=float,
        default=1.0,
        help="Weight for worst_case_over_T_loss term in CompositeMetamorphicLoss",
    )
    parser.add_argument(
        "--target-mapped-weight",
        type=float,
        default=0.0,
        help="Weight for target-mapped supervised term from relation tests with target_transform",
    )
    parser.add_argument(
        "--acc-tolerance",
        type=float,
        default=None,
        help="Optional absolute tolerance for acc@tol on test metrics",
    )
    parser.add_argument(
        "--acc-tolerance-scale",
        choices=("raw", "normalized"),
        default="raw",
        help="Scale for --acc-tolerance. raw uses target units from metadata; normalized uses model output units",
    )
    parser.add_argument(
        "--acc-tolerance-ratio",
        type=float,
        default=0.05,
        help="Default acc@tol ratio when --acc-tolerance is omitted (applied to raw range when metadata exists)",
    )
    parser.add_argument(
        "--output-scale",
        choices=("auto", "normalized", "raw"),
        default="auto",
        help="Interpretation of dataset targets stored in items['out']; auto infers from observed values + metadata",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Optional cap on number of samples loaded after the header (for quick experiments)",
    )
    parser.add_argument(
        "--include-target-mapped-over-T-transforms",
        action="store_true",
        help="Include optional target-mapped over-T transforms from the catalog (experimental heuristics)",
    )
    parser.add_argument(
        "--print-json",
        action="store_true",
        help="Print summary as JSON after the human-readable report",
    )
    parser.add_argument(
        "--debug-rule-name",
        type=str,
        default="radiation_up_implies_active_power_non_decreasing",
        help="Rule name to trace per-epoch during training",
    )
    parser.add_argument(
        "--debug-log-every",
        type=int,
        default=1,
        help="Log rule diagnostics every N epochs (<=0 disables)",
    )
    parser.add_argument(
        "--debug-max-batches",
        type=int,
        default=4,
        help="Max batches per split used for rule diagnostics (<=0 uses all batches)",
    )
    args = parser.parse_args()

    jsonl_header, md_header = load_headers(args.jsonl, args.md)
    headers_match = jsonl_header == md_header
    print(f"metadata_header_match(jsonl,md)={headers_match}")
    if not headers_match:
        print("WARNING: headers differ between .jsonl and .md")

    loader = DatasetLoader(str(args.jsonl))
    items = loader.load()
    if args.limit is not None:
        items = items[: args.limit]

    if len(items) < 10:
        raise ValueError(f"Need more samples for comparison, got {len(items)}")

    input_variables = loader.get_input_variables()
    means = loader.get_means()
    stds = loader.get_stds()
    lookback = loader.get_lookback() or 1
    out_min = loader.get_out_min()
    out_max = loader.get_out_max()
    device = Device.get_device()

    split = split_items(
        items,
        seed=args.seed,
        train_ratio=args.train_ratio,
        val_ratio=args.val_ratio,
        test_ratio=args.test_ratio,
    )
    print(
        f"split sizes: train={len(split.train_items)} val={len(split.val_items)} test={len(split.test_items)} "
        f"device={device}"
    )

    inferred_output_scale = infer_output_scale(split.test_items or items, out_min, out_max)
    output_scale = inferred_output_scale if args.output_scale == "auto" else args.output_scale
    tolerance, tolerance_info = resolve_acc_tolerance(
        explicit_tolerance=args.acc_tolerance,
        explicit_tolerance_scale=args.acc_tolerance_scale,
        default_ratio=args.acc_tolerance_ratio,
        output_scale=output_scale,
        eval_items=split.test_items,
        out_min=out_min,
        out_max=out_max,
    )
    print(
        "acc@tol_config: "
        f"enabled={tolerance_info.get('enabled', False)} "
        f"output_scale={output_scale} "
        f"(inferred={inferred_output_scale}) "
        f"source={tolerance_info.get('source', tolerance_info.get('reason'))} "
        f"model_tol={tolerance_info.get('model_value', float('nan'))} "
        f"raw_tol={tolerance_info.get('raw_value', float('nan'))}"
    )

    config = TrainingConfig(
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        seed=args.seed,
    )

    numerical_t_feature_names = list(input_variables)[-len(means):]
    categorical_t_feature_count = 0
    if split.train_items:
        sample_batch_for_shape = next(
            iter(make_loader(split.train_items[: min(4, len(split.train_items))], 4, shuffle=False)))
        categorical_t_feature_count = (
            sample_batch_for_shape["categorical_t_features"].shape[-1]
            if sample_batch_for_shape["categorical_t_features"].dim() > 1
            else 0
        )
    rule_specs = build_solar_plant_active_power_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        categorical_t_feature_count=categorical_t_feature_count,
        include_target_mapped=args.include_target_mapped_over_T_transforms,
    )
    rule_specs, effective_rule_weights, inactive_configured_rules = apply_rule_weight_map(
        rule_specs=rule_specs,
        rule_weight_map=RULE_WEIGHT_MAP,
    )
    rule_summary = summarize_rule_specs(rule_specs)
    print(f"rule_weight_map={effective_rule_weights}")
    if inactive_configured_rules:
        print(f"rule_weight_map(inactive_for_this_run)={inactive_configured_rules}")
    print(
        "catalog: "
        f"specs={rule_summary['num_specs']} "
        f"relation_tests={rule_summary['num_relation_tests']} "
        f"over_T_transforms={rule_summary['num_over_T_transforms']} "
        f"by_category={rule_summary['by_category']}"
    )

    assignment_probe = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_weight=0.0,
        relation_constraint_weight=0.0,
        worst_case_over_T_weight=0.0,
    )
    relation_constraints = assignment_probe.assigned_relation_constraints
    over_T_transform_set = assignment_probe.assigned_over_T_transform_set
    debug_rule_test = find_relation_test_by_name(relation_constraints, args.debug_rule_name)
    debug_log_every = max(0, int(args.debug_log_every))
    debug_max_batches = None if int(args.debug_max_batches) <= 0 else int(args.debug_max_batches)
    if debug_log_every > 0 and debug_rule_test is None:
        available_rule_names = sorted([test.name for test in relation_constraints if test.name])
        print(
            f"WARNING: debug rule '{args.debug_rule_name}' not found. "
            f"Available relation rules: {available_rule_names}"
        )
    print(
        "catalog_assignment(exclusive): "
        f"relation_constraints={assignment_probe.rule_assignment_summary['assigned_relation_constraints']} "
        f"over_T_transforms={assignment_probe.rule_assignment_summary['assigned_over_T_transforms']} "
        f"fallback_to_relation={assignment_probe.rule_assignment_summary['fallback_to_relation']} "
        f"fallback_to_over_T={assignment_probe.rule_assignment_summary['fallback_to_over_T']} "
        f"dropped={assignment_probe.rule_assignment_summary['dropped_rule_specs']}"
    )
    if args.worst_case_over_T_weight > 0 and len(over_T_transform_set) == 0:
        print(
            "WARNING: worst_case_over_T_loss is enabled but over_T_transform_set is empty; "
            "the composite loss will reduce to supervised-only for this branch."
        )
    has_relation_constraints = assignment_probe.rule_assignment_summary["assigned_relation_constraints"] > 0
    has_over_t_transforms = assignment_probe.rule_assignment_summary["assigned_over_T_transforms"] > 0
    has_target_mapped_rules = rule_summary["by_category"].get("target_mapped", 0) > 0
    print(
        "rule_weight_config: "
        f"supervised={args.supervised_weight} "
        f"relation_constraint={args.relation_constraint_weight} "
        f"worst_case_over_T={args.worst_case_over_T_weight} "
        f"target_mapped={args.target_mapped_weight} "
        f"active_relation={has_relation_constraints} "
        f"active_over_T={has_over_t_transforms} "
        f"active_target_mapped={has_target_mapped_rules}"
    )

    transform_consistency_report = {"checked_transforms": 0, "errors": [], "warnings": [], "is_valid": True}
    if split.train_items:
        sample_validation_batch = next(
            iter(make_loader(split.train_items[: min(8, len(split.train_items))], 4, shuffle=False)))
        transform_consistency_report = validate_metamorphic_transforms_on_batch(
            sample_validation_batch,
            relation_tests=relation_constraints,
            transform_set=over_T_transform_set,
        )
        print(
            "transform_consistency: "
            f"checked={transform_consistency_report['checked_transforms']} "
            f"errors={len(transform_consistency_report['errors'])} "
            f"warnings={len(transform_consistency_report['warnings'])}"
        )
        for warning in transform_consistency_report["warnings"][:5]:
            print(f"  - warning: {warning}")
        for error in transform_consistency_report["errors"][:5]:
            print(f"  - error: {error}")

    baseline_loss = torch.nn.MSELoss()
    baseline_result = train_model(
        split=split,
        input_variables=input_variables,
        lookback=lookback,
        means=means,
        stds=stds,
        device=device,
        loss_fn=baseline_loss,
        config=config,
        run_name="baseline",
        debug_rule_test=debug_rule_test,
        debug_log_every=debug_log_every,
        debug_max_batches=debug_max_batches,
    )
    baseline_result.test_metrics = evaluate_regression(
        baseline_result.model,
        make_loader(split.test_items, args.batch_size, shuffle=False),
        device,
        tolerance=tolerance,
    )

    composite_loss = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_loss=torch.nn.MSELoss(),
        supervised_weight=args.supervised_weight,
        relation_constraint_weight=args.relation_constraint_weight,
        worst_case_over_T_weight=args.worst_case_over_T_weight,
        target_mapped_weight=args.target_mapped_weight,
    )
    composite_result = train_model(
        split=split,
        input_variables=input_variables,
        lookback=lookback,
        means=means,
        stds=stds,
        device=device,
        loss_fn=composite_loss,
        config=config,
        run_name="composite",
        debug_rule_test=debug_rule_test,
        debug_log_every=debug_log_every,
        debug_max_batches=debug_max_batches,
    )
    composite_result.test_metrics = evaluate_regression(
        composite_result.model,
        make_loader(split.test_items, args.batch_size, shuffle=False),
        device,
        tolerance=tolerance,
    )

    results: dict[str, ModelResult] = {
        "baseline": baseline_result,
        "composite": composite_result,
    }

    for mode_name, result in results.items():
        result.worst_case_over_T_report = evaluate_worst_case_over_T(
            result.model,
            make_loader(split.test_items, args.batch_size, shuffle=False),
            transform_set=over_T_transform_set,
            tolerance=tolerance,
        )
        if relation_constraints:
            result.violation_report = compute_violation_report(
                result.model,
                make_loader(split.test_items, args.batch_size, shuffle=False),
                metamorphic_tests=relation_constraints,
            )

    print_report("Baseline (MSE)", baseline_result)
    print_report("Composite Metamorphic", composite_result)

    delta = compute_delta_against_baseline(composite_result, baseline_result, prefix="composite")
    deltas_vs_baseline: dict[str, dict[str, float]] = {"composite": delta}
    print("\n[Delta composite - baseline]")
    for key, value in delta.items():
        print(f"{key}={value:.6f}")

    if args.print_json:
        payload_results = {}
        for mode_name, result in results.items():
            payload_results[mode_name] = {
                "test_metrics": result.test_metrics,
                "last_epoch": result.train_history[-1] if result.train_history else None,
                "violation_report": result.violation_report,
                "worst_case_over_T_report": result.worst_case_over_T_report,
            }
        payload = {
            "headers_match": headers_match,
            "split": {
                "train": len(split.train_items),
                "val": len(split.val_items),
                "test": len(split.test_items),
            },
            "compare_modes": ["baseline", "composite"],
            "acc_tolerance": tolerance_info,
            "catalog": {
                "summary": rule_summary,
                "assignment_summary": assignment_probe.rule_assignment_summary,
                "transform_consistency_report": transform_consistency_report,
            },
            "results": payload_results,
            "deltas_vs_baseline": deltas_vs_baseline,
        }
        print("\n[JSON]")
        print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
