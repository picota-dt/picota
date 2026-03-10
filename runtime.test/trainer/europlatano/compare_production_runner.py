import argparse
import json
import math
import random
import re
import sys
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path

import torch

ROOT = Path(__file__).resolve().parents[2]
RUNTIME_TRAINER_ROOT = ROOT.parent / "runtime.trainer"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(RUNTIME_TRAINER_ROOT) not in sys.path:
    sys.path.insert(0, str(RUNTIME_TRAINER_ROOT))

import Device
from kan.DatasetLoader import DatasetLoader
from kan.MetamorphicCatalog import (
    CatalogRuleSpec,
    RuleCategory,
    make_equal_test,
    make_greater_or_equal_test,
    make_transform,
    scale_target,
    summarize_rule_specs,
)
from kan.MetamorphicLoss import Batch, BatchTransform, CompositeMetamorphicLoss
from kan.MetamorphicLoss import MetamorphicTest, Proportional
from trainer.house.compare_temperature_runner import (
    TrainingConfig,
    compute_delta_against_baseline,
    evaluate_regression,
    infer_feature_layout,
    infer_output_scale,
    load_headers,
    make_loader,
    print_report,
    resolve_acc_tolerance,
    split_items,
    train_model,
)
from trainer.metamorphic_evaluation import (
    compute_over_T_violation_report,
    compute_violation_report,
    evaluate_worst_case_over_T,
    validate_metamorphic_transforms_on_batch,
)


class SkipDatasetError(Exception):
    pass


def _canonical_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.strip().lower())


def _find_feature_index(feature_names: list[str], aliases: list[str]) -> int | None:
    canonical_to_index = {_canonical_name(name): idx for idx, name in enumerate(feature_names)}
    alias_canon = [_canonical_name(alias) for alias in aliases]

    for alias in alias_canon:
        if alias in canonical_to_index:
            return canonical_to_index[alias]

    for idx, feature_name in enumerate(feature_names):
        canon = _canonical_name(feature_name)
        if any(alias and alias in canon for alias in alias_canon):
            return idx
    return None


def _add_humidity_noise_in_humid_conditions(
        humidity_index: int,
        noise_ratio: float,
) -> BatchTransform:
    def _resolve_humidity_threshold(values: torch.Tensor) -> float:
        max_abs = float(torch.max(torch.abs(values)).item()) if values.numel() > 0 else 0.0
        return 0.70 if max_abs <= 1.5 else 70.0

    def _apply_noise(values: torch.Tensor) -> torch.Tensor:
        threshold = _resolve_humidity_threshold(values)
        humid_mask = values >= threshold
        if not torch.any(humid_mask):
            return values

        # Relative random noise: Noise(0.02) -> +/-2% perturbations around 1.0 scale.
        relative_noise = noise_ratio * torch.randn_like(values)
        perturbed = values * (1.0 + relative_noise)
        out = values.clone()
        out[humid_mask] = perturbed[humid_mask]
        return out

    def _transform(batch: Batch) -> Batch:
        numerical_t = batch["numerical_t_features"].clone()
        if numerical_t.shape[-1] > humidity_index:
            numerical_t[..., humidity_index] = _apply_noise(numerical_t[..., humidity_index])
        batch["numerical_t_features"] = numerical_t

        if "numerical_lookback_features" in batch:
            numerical_lb = batch["numerical_lookback_features"].clone()
            if numerical_lb.shape[-1] > humidity_index:
                numerical_lb[..., humidity_index] = _apply_noise(numerical_lb[..., humidity_index])
            batch["numerical_lookback_features"] = numerical_lb
        return batch

    return _transform


def _scale_numerical_feature_current_and_lookback(index: int, factor: float) -> BatchTransform:
    def _transform(batch: Batch) -> Batch:
        numerical_t = batch["numerical_t_features"].clone()
        if numerical_t.shape[-1] > index:
            numerical_t[..., index] = numerical_t[..., index] * factor
        batch["numerical_t_features"] = numerical_t

        if "numerical_lookback_features" in batch:
            numerical_lb = batch["numerical_lookback_features"].clone()
            if numerical_lb.shape[-1] > index:
                numerical_lb[..., index] = numerical_lb[..., index] * factor
            batch["numerical_lookback_features"] = numerical_lb
        return batch

    return _transform


def build_europlatano_production_rule_specs(
        numerical_t_feature_names: list[str],
        out_min: float | None,
        out_max: float | None,
) -> tuple[list[CatalogRuleSpec], list[str]]:
    specs: list[CatalogRuleSpec] = []
    skipped_rules: list[str] = []

    area_idx = _find_feature_index(
        numerical_t_feature_names,
        aliases=[
            "Territory.Farm.Area",
            "Farm.Area",
            "Area",
        ],
    )
    if area_idx is not None:
        for area_factor in (1.05, 1.10, 1.20):
            name_suffix = f"{area_factor:.2f}".replace(".", "_")
            rule_name = f"production_proportional_to_surface_area_x{name_suffix}"
            area_up = _scale_numerical_feature_current_and_lookback(index=area_idx, factor=area_factor)
            specs.append(
                CatalogRuleSpec(
                    name=rule_name,
                    category=RuleCategory.TARGET_MAPPED,
                    over_T_transform=make_transform(
                        transform=area_up,
                        target_transform=scale_target(area_factor),
                        name=rule_name,
                    ),
                    description=(
                        "Production should be proportional to surface area scaling "
                        f"({(area_factor - 1.0) * 100:+.0f}%)."
                    ),
                    consistency_profile=f"current_and_lookback_area_scaling_x{name_suffix}",
                )
            )
    else:
        skipped_rules.append("production_proportional_to_surface_area (missing Area feature)")

    precipitation_idx = _find_feature_index(
        numerical_t_feature_names,
        aliases=[
            "Territory.Precipitation",
            "Precipitation",
            "Rainfall",
            "prec",
        ],
    )
    if precipitation_idx is not None:
        rainfall_up = _scale_numerical_feature_current_and_lookback(index=precipitation_idx, factor=1.10)
        specs.append(
            CatalogRuleSpec(
                name="production_non_decreasing_when_rainfall_increases",
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_greater_or_equal_test(
                    rainfall_up,
                    name="production_non_decreasing_when_rainfall_increases",
                    weight=1.0,
                    violation_atol=0.0,
                    violation_rtol=0.0,
                ),
                description="Production should not decrease when precipitation increases (+10%).",
                consistency_profile="current_and_lookback_precipitation_scaling",
            )
        )
    else:
        skipped_rules.append("production_non_decreasing_when_rainfall_increases (missing Precipitation feature)")

    humidity_idx = _find_feature_index(
        numerical_t_feature_names,
        aliases=[
            "Territory.Humidity",
            "Humidity",
            "hrMedia",
        ],
    )
    if humidity_idx is not None:
        humidity_noise = _add_humidity_noise_in_humid_conditions(humidity_index=humidity_idx, noise_ratio=0.02)
        specs.append(
            CatalogRuleSpec(
                name="production_robust_to_humidity_noise_in_humid_conditions",
                category=RuleCategory.INVARIANCE,
                relation_test=make_equal_test(
                    humidity_noise,
                    name="production_robust_to_humidity_noise_in_humid_conditions",
                    weight=1.0,
                    violation_atol=0.0,
                    violation_rtol=0.05,
                ),
                description="Production should remain stable under small humidity noise (2%) in humid conditions.",
                consistency_profile="humidity_noise_in_humid_region",
            )
        )
    else:
        skipped_rules.append("production_robust_to_humidity_noise_in_humid_conditions (missing Humidity feature)")

    return specs, skipped_rules


def _resolve_dataset_paths(args) -> tuple[Path, Path]:
    if (args.jsonl is None) != (args.md is None):
        raise ValueError("Use both --jsonl and --md together, or neither in --all-datasets mode.")
    if args.jsonl is None or args.md is None:
        raise ValueError("For single dataset mode provide --jsonl and --md.")
    return args.jsonl, args.md


def _discover_dataset_pairs(data_dir: Path) -> list[tuple[Path, Path]]:
    pairs: list[tuple[Path, Path]] = []
    for md_path in sorted(data_dir.glob("*.md")):
        stem = md_path.stem
        base_stem = stem.split("+", 1)[0] if "+" in stem else stem
        jsonl_path = data_dir / f"{base_stem}.jsonl"
        if not jsonl_path.exists():
            continue
        pairs.append((jsonl_path, md_path))
    return pairs


def _mean_finite(values: list[float]) -> float | None:
    finite = [value for value in values if isinstance(value, (int, float)) and math.isfinite(value)]
    if not finite:
        return None
    return float(sum(finite) / len(finite))


def _summarize_batch_reports(success_reports: list[dict], failures: list[dict]) -> dict:
    delta_values: dict[str, list[float]] = {}
    better_counts = {
        "delta_mae_composite_minus_base": 0,
        "delta_rmse_composite_minus_base": 0,
        "delta_worst_case_mae_over_T_composite_minus_base": 0,
    }
    worse_counts = {key: 0 for key in better_counts}
    equal_counts = {key: 0 for key in better_counts}

    baseline_violation_rates = []
    composite_violation_rates = []

    for report in success_reports:
        delta = report.get("deltas_vs_baseline", {}).get("composite", {})
        for key, value in delta.items():
            if not isinstance(value, (int, float)) or not math.isfinite(value):
                continue
            delta_values.setdefault(key, []).append(float(value))
            if key in better_counts:
                if value < 0:
                    better_counts[key] += 1
                elif value > 0:
                    worse_counts[key] += 1
                else:
                    equal_counts[key] += 1

        baseline_vr = report.get("results", {}).get("baseline", {}).get("violation_report", {}).get(
            "overall_violation_rate"
        )
        if isinstance(baseline_vr, (int, float)) and math.isfinite(float(baseline_vr)):
            baseline_violation_rates.append(float(baseline_vr))
        composite_vr = report.get("results", {}).get("composite", {}).get("violation_report", {}).get(
            "overall_violation_rate"
        )
        if isinstance(composite_vr, (int, float)) and math.isfinite(float(composite_vr)):
            composite_violation_rates.append(float(composite_vr))

    mean_delta = {key: _mean_finite(values) for key, values in delta_values.items()}
    mean_delta = {key: value for key, value in mean_delta.items() if value is not None}

    return {
        "num_success": len(success_reports),
        "num_failed": len(failures),
        "mean_delta_metrics": mean_delta,
        "composite_better_counts": better_counts,
        "composite_worse_counts": worse_counts,
        "composite_equal_counts": equal_counts,
        "mean_baseline_violation_rate": _mean_finite(baseline_violation_rates),
        "mean_composite_violation_rate": _mean_finite(composite_violation_rates),
    }


def _precheck_dataset_rows(jsonl_path: Path, limit: int | None = None) -> tuple[int, int, int, int]:
    """
    Returns:
      rows_before_filter, rows_filtered_output_zero, rows_after_filter, rows_after_limit
    """
    loader = DatasetLoader(str(jsonl_path))
    items = loader.load()
    rows_before_filter = len(items)
    filtered_items = [item for item in items if float(item.get("out", 0.0)) != 0.0]
    rows_after_filter = len(filtered_items)
    rows_filtered_output_zero = rows_before_filter - rows_after_filter
    rows_after_limit = rows_after_filter if limit is None else min(rows_after_filter, int(limit))
    return rows_before_filter, rows_filtered_output_zero, rows_after_filter, rows_after_limit


def _clone_batch(batch: Batch) -> Batch:
    cloned: Batch = {}
    for key, value in batch.items():
        cloned[key] = value.clone() if torch.is_tensor(value) else deepcopy(value)
    return cloned


def _find_proportional_test(relation_constraints: list) -> MetamorphicTest | None:
    for test in relation_constraints:
        if isinstance(getattr(test, "relation", None), Proportional):
            return test
    return None


def _to_proportional_relation_space(values: torch.Tensor, relation: Proportional) -> torch.Tensor:
    raw_min = getattr(relation, "raw_out_min", None)
    raw_max = getattr(relation, "raw_out_max", None)
    if raw_min is None or raw_max is None or not raw_max > raw_min:
        return values
    span = float(raw_max) - float(raw_min)
    return values * span + float(raw_min)


def _debug_log_proportional_samples(
        model,
        split_test_items: list[dict],
        batch_size: int,
        seed: int,
        model_label: str,
        proportional_test: MetamorphicTest | None,
        area_index: int | None,
        max_samples: int = 30,
) -> None:
    if proportional_test is None:
        print(f"[MR debug {model_label}] proportional_test=missing")
        return
    if area_index is None:
        print(f"[MR debug {model_label}] area_feature_index=missing")
        return
    if not split_test_items:
        print(f"[MR debug {model_label}] test_split_empty")
        return

    sample_n = min(max_samples, len(split_test_items))
    rng = random.Random(seed)
    picked_indexes = rng.sample(range(len(split_test_items)), sample_n)
    sampled_items = [split_test_items[i] for i in picked_indexes]
    sampled_loader = make_loader(sampled_items, batch_size=sample_n, shuffle=False)
    sampled_batch = next(iter(sampled_loader))

    with torch.no_grad():
        base_pred = model(sampled_batch).squeeze().reshape(-1)
        transformed_batch = proportional_test.transform(_clone_batch(sampled_batch))
        transformed_pred = model(transformed_batch).squeeze().reshape(-1)

    relation = proportional_test.relation
    base_relation = _to_proportional_relation_space(base_pred, relation)
    transformed_relation = _to_proportional_relation_space(transformed_pred, relation)
    expected = base_relation * float(getattr(relation, "factor", 1.0))

    local_atol = 1e-6 if getattr(proportional_test, "violation_atol", None) is None else float(
        proportional_test.violation_atol)
    local_rtol = 1e-4 if getattr(proportional_test, "violation_rtol", None) is None else float(
        proportional_test.violation_rtol)

    residual = torch.abs(transformed_relation - expected)
    threshold = local_atol + local_rtol * torch.abs(expected)
    violated = residual > threshold

    area_before = sampled_batch["numerical_t_features"][..., area_index].reshape(-1)
    area_after = transformed_batch["numerical_t_features"][..., area_index].reshape(-1)

    print(
        f"[MR debug {model_label}] proportional_samples={sample_n} "
        f"factor={float(getattr(relation, 'factor', 1.0))} "
        f"atol={local_atol} rtol={local_rtol}"
    )
    for row_idx in range(sample_n):
        print(
            "[MR debug sample] "
            f"i={row_idx + 1}/{sample_n} "
            f"area={float(area_before[row_idx].item()):.6f} "
            f"area_t={float(area_after[row_idx].item()):.6f} "
            f"y_pred={float(base_relation[row_idx].item()):.6f} "
            f"y_pred_t={float(transformed_relation[row_idx].item()):.6f} "
            f"expected={float(expected[row_idx].item()):.6f} "
            f"residual={float(residual[row_idx].item()):.6f} "
            f"threshold={float(threshold[row_idx].item()):.6f} "
            f"violated={bool(violated[row_idx].item())}"
        )


def _print_over_t_violation_report(report: dict | None) -> None:
    if report is None:
        return
    if not report.get("available", False):
        print(
            "over_T_violation_rate: "
            f"not_available reason={report.get('reason')} "
            f"num_transforms={report.get('num_transforms', 0)}"
        )
        return

    print(
        "over_T_violation_rate: "
        f"overall={report['overall_violation_rate']:.6f} "
        f"({report['total_violations']}/{report['total_cases']})"
    )
    for transform_name, stats in sorted(report["by_transform"].items()):
        print(
            f"  - {transform_name}: rate={stats['violation_rate']:.6f} "
            f"({stats['violations']}/{stats['total']})"
        )


def _print_execution_config(args, mode: str, total_datasets: int | None = None) -> None:
    if mode == "all_datasets":
        print(
            "run_config: "
            f"mode={mode} "
            f"data_dir={args.data_dir} "
            f"requested_total={total_datasets if total_datasets is not None else 'n/a'} "
            f"max_datasets={args.max_datasets} "
            f"fail_fast={args.fail_fast}"
        )
    else:
        print(
            "run_config: "
            f"mode={mode} "
            f"jsonl={args.jsonl} "
            f"md={args.md}"
        )

    print(
        "train_config: "
        f"epochs={args.epochs} "
        f"batch_size={args.batch_size} "
        f"lr={args.lr} "
        f"seed={args.seed} "
        f"split(train/val/test)=({args.train_ratio:.2f}/{args.val_ratio:.2f}/{args.test_ratio:.2f})"
    )
    print(
        "loss_weights: "
        f"supervised={args.supervised_weight} "
        f"relation_constraint={args.relation_constraint_weight} "
        f"worst_case_over_T={args.worst_case_over_T_weight} "
        f"target_mapped={args.target_mapped_weight}"
    )
    print(
        "metric_config: "
        f"output_scale={args.output_scale} "
        f"acc_tolerance={args.acc_tolerance} "
        f"acc_tolerance_scale={args.acc_tolerance_scale} "
        f"acc_tolerance_ratio={args.acc_tolerance_ratio} "
        f"limit={args.limit} "
        f"debug_proportional_samples={args.debug_proportional_samples}"
    )


def _resolve_lookback_steps(tensor: torch.Tensor) -> int:
    if not torch.is_tensor(tensor) or tensor.numel() == 0:
        return 0
    if tensor.dim() == 1:
        return 1
    if tensor.dim() == 2:
        # Collate may collapse lookback=1 to [B, F].
        return 1
    return int(tensor.shape[1])


def run_single_comparison(jsonl_path: Path, md_path: Path, args, print_details: bool = True) -> dict:
    jsonl_path = jsonl_path.resolve()
    md_path = md_path.resolve()
    jsonl_header, md_header = load_headers(jsonl_path, md_path)
    _ = jsonl_header == md_header

    loader = DatasetLoader(str(jsonl_path))
    items = loader.load()
    total_rows_before_filter = len(items)
    items = [item for item in items if float(item.get("out", 0.0)) != 0.0]
    filtered_zero_output_rows = total_rows_before_filter - len(items)

    if len(items) == 0:
        raise SkipDatasetError("dataset is empty after filtering rows with out == 0")

    if args.limit is not None:
        items = items[: args.limit]
    if len(items) < 10:
        raise SkipDatasetError(f"not enough samples after filtering (n={len(items)})")

    if print_details:
        print(
            f"rows_before_filter={total_rows_before_filter} "
            f"rows_filtered_output_zero={filtered_zero_output_rows} "
            f"rows_after_filter={len(items)}"
        )

    input_variables = loader.get_input_variables()
    means = loader.get_means()
    stds = loader.get_stds()
    lookback = loader.get_lookback()
    if lookback is None:
        lookback = 1
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
    if print_details:
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
    if print_details:
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

    numerical_t_feature_names: list[str] = []
    if split.train_items:
        sample_batch_for_shape = next(
            iter(make_loader(split.train_items[: min(4, len(split.train_items))], 4, shuffle=False))
        )
        t_feature_count, numerical_t_feature_names, categorical_t_feature_count = infer_feature_layout(
            input_variables=input_variables,
            sample_batch=sample_batch_for_shape,
        )
        observed_lookback_t = _resolve_lookback_steps(sample_batch_for_shape["lookback_t"])
        observed_lookback_num = _resolve_lookback_steps(sample_batch_for_shape["numerical_lookback_features"])
        observed_lookback_cat = _resolve_lookback_steps(sample_batch_for_shape["categorical_lookback_features"])

        if observed_lookback_t != lookback or observed_lookback_num != lookback or observed_lookback_cat != lookback:
            raise ValueError(
                "Lookback mismatch between metadata and tensors: "
                f"metadata={lookback}, "
                f"lookback_t={observed_lookback_t}, "
                f"numerical_lookback={observed_lookback_num}, "
                f"categorical_lookback={observed_lookback_cat}"
            )

        observed_input_width = (
                t_feature_count
                + len(numerical_t_feature_names)
                + categorical_t_feature_count
                + (observed_lookback_t * t_feature_count)
                + (observed_lookback_num * len(numerical_t_feature_names))
                + (observed_lookback_cat * categorical_t_feature_count)
        )
        expected_model_width = len(input_variables) * (1 + lookback)
        if observed_input_width != expected_model_width:
            raise ValueError(
                "Input width mismatch for lookback expansion: "
                f"expected={expected_model_width} observed={observed_input_width}"
            )
        if print_details:
            print(
                "lookback_config: "
                f"metadata={lookback} "
                f"observed_t={observed_lookback_t} "
                f"observed_num={observed_lookback_num} "
                f"observed_cat={observed_lookback_cat} "
                f"input_features={len(input_variables)} "
                f"expected_width={expected_model_width} "
                f"observed_width={observed_input_width}"
            )

    rule_specs, skipped_rules = build_europlatano_production_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        out_min=out_min,
        out_max=out_max,
    )
    rule_summary = summarize_rule_specs(rule_specs)
    if print_details:
        print(
            "catalog: "
            f"specs={rule_summary['num_specs']} "
            f"relation_tests={rule_summary['num_relation_tests']} "
            f"over_T_transforms={rule_summary['num_over_T_transforms']} "
            f"by_category={rule_summary['by_category']}"
        )
        if skipped_rules:
            print(f"catalog_skipped_rules={len(skipped_rules)}")
            for skipped in skipped_rules:
                print(f"  - {skipped}")

    assignment_probe = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_weight=0.0,
        relation_constraint_weight=0.0,
        worst_case_over_T_weight=0.0,
    )
    relation_constraints = assignment_probe.assigned_relation_constraints
    over_T_transform_set = assignment_probe.assigned_over_T_transform_set
    area_feature_index = _find_feature_index(
        numerical_t_feature_names,
        aliases=[
            "Territory.Farm.Area",
            "Farm.Area",
            "Area",
        ],
    )
    proportional_test = _find_proportional_test(relation_constraints)
    if print_details:
        print(
            "catalog_assignment(exclusive): "
            f"relation_constraints={assignment_probe.rule_assignment_summary['assigned_relation_constraints']} "
            f"over_T_transforms={assignment_probe.rule_assignment_summary['assigned_over_T_transforms']} "
            f"fallback_to_relation={assignment_probe.rule_assignment_summary['fallback_to_relation']} "
            f"fallback_to_over_T={assignment_probe.rule_assignment_summary['fallback_to_over_T']} "
            f"dropped={assignment_probe.rule_assignment_summary['dropped_rule_specs']}"
        )

    transform_consistency_report = {"checked_transforms": 0, "errors": [], "warnings": [], "is_valid": True}
    if split.train_items:
        sample_validation_batch = next(
            iter(make_loader(split.train_items[: min(8, len(split.train_items))], 4, shuffle=False))
        )
        transform_consistency_report = validate_metamorphic_transforms_on_batch(
            sample_validation_batch,
            relation_tests=relation_constraints,
            transform_set=over_T_transform_set,
        )
        if print_details:
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

    baseline_result = train_model(
        split=split,
        input_variables=input_variables,
        lookback=lookback,
        means=means,
        stds=stds,
        device=device,
        loss_fn=torch.nn.MSELoss(),
        config=config,
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
    )
    composite_result.test_metrics = evaluate_regression(
        composite_result.model,
        make_loader(split.test_items, args.batch_size, shuffle=False),
        device,
        tolerance=tolerance,
    )

    results = {
        "baseline": baseline_result,
        "composite": composite_result,
    }

    for _, result in results.items():
        result.worst_case_over_T_report = evaluate_worst_case_over_T(
            result.model,
            make_loader(split.test_items, args.batch_size, shuffle=False),
            transform_set=over_T_transform_set,
            tolerance=tolerance,
        )
        result.over_T_violation_report = compute_over_T_violation_report(
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

    if print_details and args.debug_proportional_samples > 0:
        _debug_log_proportional_samples(
            model=baseline_result.model,
            split_test_items=split.test_items,
            batch_size=args.batch_size,
            seed=args.seed,
            model_label="Baseline",
            proportional_test=proportional_test,
            area_index=area_feature_index,
            max_samples=args.debug_proportional_samples,
        )
        _debug_log_proportional_samples(
            model=composite_result.model,
            split_test_items=split.test_items,
            batch_size=args.batch_size,
            seed=args.seed,
            model_label="Composite",
            proportional_test=proportional_test,
            area_index=area_feature_index,
            max_samples=args.debug_proportional_samples,
        )

    if print_details:
        print_report("Baseline (MSE)", baseline_result)
        _print_over_t_violation_report(getattr(baseline_result, "over_T_violation_report", None))
        print_report("Composite Metamorphic", composite_result)
        _print_over_t_violation_report(getattr(composite_result, "over_T_violation_report", None))

    delta = compute_delta_against_baseline(composite_result, baseline_result, prefix="composite")
    if print_details:
        print("\n[Delta composite - baseline]")
        for key, value in delta.items():
            print(f"{key}={value:.6f}")

    payload_results = {}
    for mode_name, result in results.items():
        payload_results[mode_name] = {
            "test_metrics": result.test_metrics,
            "last_epoch": result.train_history[-1] if result.train_history else None,
            "violation_report": result.violation_report,
            "worst_case_over_T_report": result.worst_case_over_T_report,
            "over_T_violation_report": getattr(result, "over_T_violation_report", None),
        }

    return {
        "dataset": {
            "id": jsonl_path.stem,
        },
        "rows": {
            "before_filter": total_rows_before_filter,
            "filtered_output_zero": filtered_zero_output_rows,
            "after_filter": len(items),
        },
        "split": {
            "train": len(split.train_items),
            "val": len(split.val_items),
            "test": len(split.test_items),
        },
        "compare_modes": ["baseline", "composite"],
        "acc_tolerance": tolerance_info,
        "catalog": {
            "summary": rule_summary,
            "skipped_rules": skipped_rules,
            "assignment_summary": assignment_probe.rule_assignment_summary,
            "transform_consistency_report": transform_consistency_report,
        },
        "results": payload_results,
        "deltas_vs_baseline": {"composite": delta},
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Compare baseline vs composite metamorphic training on Europlatano production."
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=ROOT / "data" / "europlatano" / "datasets" / "day" / "no_split" / "W10",
        help="Directory with europlataºo pairs (e.g. produccion_agregada_Production:*+90.md/.jsonl).",
    )
    parser.add_argument(
        "--jsonl",
        type=Path,
        default=None,
        help="Single dataset .jsonl path (use together with --md and --no-all-datasets).",
    )
    parser.add_argument(
        "--md",
        type=Path,
        default=None,
        help="Single dataset .md path (use together with --jsonl and --no-all-datasets).",
    )
    parser.add_argument(
        "--all-datasets",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Process all discovered pairs in --data-dir (default: enabled).",
    )
    parser.add_argument(
        "--max-datasets",
        type=int,
        default=None,
        help="Optional cap for --all-datasets mode (for quick smoke runs).",
    )
    parser.add_argument(
        "--fail-fast",
        action="store_true",
        help="Stop batch processing on first dataset error.",
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--train-ratio", type=float, default=0.64)
    parser.add_argument("--val-ratio", type=float, default=0.16)
    parser.add_argument("--test-ratio", type=float, default=0.20)
    parser.add_argument(
        "--relation-constraint-weight",
        type=float,
        default=0.25,
        help="Weight for relation_constraint_penalty term in CompositeMetamorphicLoss.",
    )
    parser.add_argument(
        "--supervised-weight",
        type=float,
        default=1.0,
        help="Weight for l(f(x), y) in CompositeMetamorphicLoss.",
    )
    parser.add_argument(
        "--worst-case-over-T-weight",
        type=float,
        default=0.0,
        help="Weight for worst_case_over_T_loss term in CompositeMetamorphicLoss.",
    )
    parser.add_argument(
        "--target-mapped-weight",
        type=float,
        default=0.0,
        help="Weight for target-mapped supervised term from relation tests with target_transform.",
    )
    parser.add_argument(
        "--acc-tolerance",
        type=float,
        default=None,
        help="Optional absolute tolerance for acc@tol on test metrics.",
    )
    parser.add_argument(
        "--acc-tolerance-scale",
        choices=("raw", "normalized"),
        default="raw",
        help="Scale for --acc-tolerance. raw uses target units from metadata; normalized uses model output units.",
    )
    parser.add_argument(
        "--acc-tolerance-ratio",
        type=float,
        default=0.05,
        help="Default acc@tol ratio when --acc-tolerance is omitted.",
    )
    parser.add_argument(
        "--output-scale",
        choices=("auto", "normalized", "raw"),
        default="auto",
        help="Interpretation of dataset targets stored in items['out'].",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Optional cap on number of samples loaded after the header.",
    )
    parser.add_argument(
        "--debug-proportional-samples",
        type=int,
        default=0,
        help="Number of random test instances to log for proportional MR debugging (0 to disable).",
    )
    parser.add_argument(
        "--report-file",
        type=Path,
        default=None,
        help="Write comparison report JSON to this file.",
    )
    parser.add_argument(
        "--print-json",
        action="store_true",
        help="Print summary as JSON after the human-readable report.",
    )
    return parser


def main():
    parser = _build_parser()
    args = parser.parse_args()

    if args.all_datasets and (args.jsonl is not None or args.md is not None):
        raise ValueError("--all-datasets cannot be used together with --jsonl/--md")

    if args.all_datasets:
        dataset_pairs = _discover_dataset_pairs(args.data_dir)
        if args.max_datasets is not None:
            dataset_pairs = dataset_pairs[: args.max_datasets]
        if not dataset_pairs:
            raise ValueError(f"No valid .jsonl/.md pairs found in {args.data_dir}")

        success_reports = []
        failures = []
        skipped = []
        total = len(dataset_pairs)
        print(f"batch_mode=all_datasets total={total}")
        _print_execution_config(args, mode="all_datasets", total_datasets=total)

        for index, (jsonl_path, md_path) in enumerate(dataset_pairs, start=1):
            dataset_id = md_path.stem
            try:
                _, _, _, rows_after_limit = _precheck_dataset_rows(jsonl_path, args.limit)
                if rows_after_limit == 0:
                    skipped.append(
                        {
                            "dataset_id": dataset_id,
                            "reason": "dataset is empty after filtering rows with out == 0",
                        }
                    )
                    continue
                if rows_after_limit < 10:
                    skipped.append(
                        {
                            "dataset_id": dataset_id,
                            "reason": f"not enough samples after filtering (n={rows_after_limit})",
                        }
                    )
                    continue

                print(f"\n=== [{index}/{total}] dataset={dataset_id} ===")
                report = run_single_comparison(jsonl_path, md_path, args, print_details=True)
                success_reports.append(report)
                delta_mae = report["deltas_vs_baseline"]["composite"].get("delta_mae_composite_minus_base",
                                                                          float("nan"))
                print(f"[{index}/{total}] OK {dataset_id} delta_mae={delta_mae:.6f}")
                print(f"=== END [{index}/{total}] dataset={dataset_id} ===")
            except SkipDatasetError as exc:
                skipped_item = {
                    "dataset_id": dataset_id,
                    "reason": str(exc),
                }
                skipped.append(skipped_item)
            except Exception as exc:
                failure = {
                    "dataset_id": dataset_id,
                    "jsonl": str(jsonl_path),
                    "md": str(md_path),
                    "error": str(exc),
                }
                failures.append(failure)
                print(f"[{index}/{total}] FAIL {dataset_id} error={exc}")
                if args.fail_fast:
                    raise

        aggregate = _summarize_batch_reports(success_reports, failures)
        report_payload = {
            "mode": "all_datasets",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "data_dir": str(args.data_dir),
            "requested_total": total,
            "skipped": skipped,
            "aggregate": aggregate,
            "datasets": success_reports,
            "failures": failures,
            "config": {
                "epochs": args.epochs,
                "batch_size": args.batch_size,
                "lr": args.lr,
                "seed": args.seed,
                "train_ratio": args.train_ratio,
                "val_ratio": args.val_ratio,
                "test_ratio": args.test_ratio,
                "supervised_weight": args.supervised_weight,
                "relation_constraint_weight": args.relation_constraint_weight,
                "worst_case_over_T_weight": args.worst_case_over_T_weight,
                "target_mapped_weight": args.target_mapped_weight,
                "output_scale": args.output_scale,
                "acc_tolerance": args.acc_tolerance,
                "acc_tolerance_scale": args.acc_tolerance_scale,
                "acc_tolerance_ratio": args.acc_tolerance_ratio,
                "debug_proportional_samples": args.debug_proportional_samples,
                "limit": args.limit,
                "max_datasets": args.max_datasets,
                "fail_fast": args.fail_fast,
            },
        }

        report_file = args.report_file or (args.data_dir / "comparison_all_production_targets_report.json")
        report_file.parent.mkdir(parents=True, exist_ok=True)
        report_file.write_text(json.dumps(report_payload, indent=2), encoding="utf-8")
        print(f"report_file={report_file}")
        print(
            "batch_summary: "
            f"success={aggregate['num_success']} "
            f"failed={aggregate['num_failed']} "
            f"mean_delta_mae={aggregate['mean_delta_metrics'].get('delta_mae_composite_minus_base', float('nan')):.6f}"
        )
        if args.print_json:
            print("\n[JSON]")
            print(json.dumps(report_payload, indent=2))
        return

    jsonl_path, md_path = _resolve_dataset_paths(args)
    if not jsonl_path.exists():
        raise FileNotFoundError(f"Dataset jsonl not found: {jsonl_path}")
    if not md_path.exists():
        raise FileNotFoundError(f"Dataset md not found: {md_path}")

    _print_execution_config(args, mode="single_dataset")

    try:
        payload = run_single_comparison(jsonl_path, md_path, args, print_details=True)
    except SkipDatasetError as exc:
        return

    if args.report_file is not None:
        args.report_file.parent.mkdir(parents=True, exist_ok=True)
        args.report_file.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        print(f"report_file={args.report_file}")

    if args.print_json:
        print("\n[JSON]")
        print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
