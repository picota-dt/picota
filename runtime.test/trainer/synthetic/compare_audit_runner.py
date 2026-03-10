from __future__ import annotations

import argparse
import json
import sys
from copy import deepcopy
from dataclasses import replace
from pathlib import Path

import numpy as np
import torch

ROOT = Path(__file__).resolve().parents[2]
RUNTIME_TRAINER_ROOT = ROOT.parent / "runtime.trainer"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(RUNTIME_TRAINER_ROOT) not in sys.path:
    sys.path.insert(0, str(RUNTIME_TRAINER_ROOT))

import Device
from kan.DatasetLoader import DatasetLoader
from kan.MetamorphicCatalog import summarize_rule_specs
from kan.MetamorphicLoss import CompositeMetamorphicLoss
from trainer.house.compare_temperature_runner import (
    ModelResult,
    SplitData,
    TrainingConfig,
    compute_delta_against_baseline,
    evaluate_regression,
    infer_feature_layout,
    infer_output_scale,
    load_headers,
    make_loader,
    print_report,
    resolve_acc_tolerance,
    train_model,
)
from trainer.metamorphic_evaluation import (
    compute_violation_report,
    evaluate_worst_case_over_T,
    validate_metamorphic_transforms_on_batch,
)
from trainer.synthetic.metamorphic_rules import build_synthetic_audit_rule_specs

RULE_WEIGHTS_BY_NAME: dict[str, float] = {
    # Over-T proportionality rules (target-mapped)
    "y_proportional_to_area_x1_05_target_mapped": 0.25,
    "y_proportional_to_area_x1_10_target_mapped": 0.35,
    "y_proportional_to_area_x1_20_target_mapped": 0.50,
    # Relation constraints: monotonicity in rain
    "y_non_decreasing_when_rain_increases_x1_02": 1.0,
    "y_non_decreasing_when_rain_increases_x1_05": 1.0,
    "y_non_decreasing_when_rain_increases_x1_10": 1.0,
    # Relation constraints: invariance under humidity noise
    "y_robust_to_humidity_noise_sigma_0_01": 1.0,
    "y_robust_to_humidity_noise_sigma_0_02": 1.0,
    "y_robust_to_humidity_noise_sigma_0_05": 1.0,
}

# Global branch coefficients are kept fixed to avoid mixing branch-level tuning
# with per-rule tuning from RULE_WEIGHTS_BY_NAME.
COMPOSITE_GLOBAL_WEIGHTS = {
    "supervised_weight": 1.0,
    "relation_constraint_weight": 0.5,
    "worst_case_over_T_weight": 1.0,
    "target_mapped_weight": 0.0,
}


def split_train_and_validation(items: list[dict], seed: int, val_ratio: float) -> tuple[list[dict], list[dict]]:
    if not 0 < val_ratio < 1:
        raise ValueError("--train-val-ratio must be in (0,1)")
    if len(items) < 3:
        raise ValueError(f"Need at least 3 train samples to create train/val split, got {len(items)}")

    rng = np.random.default_rng(seed)
    indices = np.arange(len(items))
    rng.shuffle(indices)

    val_count = max(1, int(len(items) * val_ratio))
    if val_count >= len(items):
        val_count = len(items) - 1
    train_count = len(items) - val_count
    if train_count <= 0:
        raise ValueError("Invalid train/val split: train partition is empty")

    train_idx = indices[:train_count]
    val_idx = indices[train_count:]
    train_items = [items[int(i)] for i in train_idx]
    val_items = [items[int(i)] for i in val_idx]
    if not train_items or not val_items:
        raise ValueError("Invalid split produced empty train or val partition")
    return train_items, val_items


def _apply_rule_weight_overrides(rule_specs: list, weights_by_name: dict[str, float]) -> list:
    if not weights_by_name:
        return list(rule_specs)

    updated_specs = []
    known_names = {spec.name for spec in rule_specs}
    unknown_names = sorted(set(weights_by_name) - known_names)
    for unknown in unknown_names:
        print(f"WARNING: rule weight override ignored (unknown rule): {unknown}")

    for spec in rule_specs:
        override = weights_by_name.get(spec.name)
        if override is None:
            updated_specs.append(spec)
            continue
        if override < 0:
            raise ValueError(f"Rule weight must be >= 0 for '{spec.name}', got {override}")

        relation_test = spec.relation_test
        over_T_transform = spec.over_T_transform

        if relation_test is not None:
            relation_test = deepcopy(relation_test)
            relation_test.relation.weight = float(override)
        if over_T_transform is not None:
            over_T_transform = replace(over_T_transform, weight=float(override))

        updated_specs.append(
            replace(
                spec,
                relation_test=relation_test,
                over_T_transform=over_T_transform,
            )
        )
    return updated_specs


def _print_rule_weights(rule_specs: list) -> None:
    print("rule_weights:")
    for spec in rule_specs:
        category = getattr(spec.category, "value", str(spec.category))
        if getattr(spec, "relation_test", None) is not None:
            relation = spec.relation_test.relation
            kind = getattr(relation.kind, "value", str(relation.kind))
            weight = float(getattr(relation, "weight", 1.0))
            print(
                f"  - {spec.name}: category={category} branch=relation_constraints "
                f"relation={kind} weight={weight:.6f}"
            )
        if getattr(spec, "over_T_transform", None) is not None:
            over_t_weight = float(getattr(spec.over_T_transform, "weight", 1.0))
            print(
                f"  - {spec.name}: category={category} branch=over_T "
                f"weight={over_t_weight:.6f}"
            )


def run_comparison(args) -> dict:
    train_jsonl_header, train_md_header = load_headers(args.train_jsonl, args.train_md)
    test_jsonl_header, test_md_header = load_headers(args.test_jsonl, args.test_md)
    train_headers_match = train_jsonl_header == train_md_header
    test_headers_match = test_jsonl_header == test_md_header
    print(f"metadata_header_match(train_jsonl,train_md)={train_headers_match}")
    print(f"metadata_header_match(test_jsonl,test_md)={test_headers_match}")
    if not train_headers_match:
        print("WARNING: train headers differ between .jsonl and .md")
    if not test_headers_match:
        print("WARNING: test headers differ between .jsonl and .md")

    train_loader = DatasetLoader(str(args.train_jsonl))
    train_items = train_loader.load()
    if args.train_limit is not None:
        train_items = train_items[: args.train_limit]

    test_loader = DatasetLoader(str(args.test_jsonl))
    test_items = test_loader.load()
    if args.test_limit is not None:
        test_items = test_items[: args.test_limit]

    if len(train_items) < 10:
        raise ValueError(f"Need more train samples for comparison, got {len(train_items)}")
    if len(test_items) < 1:
        raise ValueError("Test dataset is empty")

    train_input_variables = train_loader.get_input_variables()
    test_input_variables = test_loader.get_input_variables()
    train_lookback = train_loader.get_lookback()
    test_lookback = test_loader.get_lookback()

    schema_match = (
            train_input_variables == test_input_variables
            and train_lookback == test_lookback
    )
    print(f"train_test_schema_match={schema_match}")
    if not schema_match:
        raise ValueError(
            "Train/test schema mismatch. "
            f"train_input_variables={train_input_variables}, test_input_variables={test_input_variables}, "
            f"train_lookback={train_lookback}, test_lookback={test_lookback}"
        )

    input_variables = train_input_variables
    means = train_loader.get_means()
    stds = train_loader.get_stds()
    lookback = train_lookback if train_lookback is not None else 1
    out_min = test_loader.get_out_min()
    out_max = test_loader.get_out_max()
    device = Device.get_device()

    fit_items, val_items = split_train_and_validation(
        train_items,
        seed=args.seed,
        val_ratio=args.train_val_ratio,
    )
    split = SplitData(
        train_items=fit_items,
        val_items=val_items,
        test_items=test_items,
    )
    print(
        f"split sizes: train={len(split.train_items)} val={len(split.val_items)} test={len(split.test_items)} "
        f"device={device}"
    )

    inferred_output_scale = infer_output_scale(split.test_items, out_min, out_max)
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

    numerical_t_feature_names: list[str] = []
    if split.train_items:
        sample_batch_for_shape = next(
            iter(make_loader(split.train_items[: min(4, len(split.train_items))], 4, shuffle=False))
        )
        _, numerical_t_feature_names, _ = infer_feature_layout(
            input_variables=input_variables,
            sample_batch=sample_batch_for_shape,
        )

    rule_specs = build_synthetic_audit_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        include_target_mapped_over_T_transforms=args.include_target_mapped_over_t_transforms,
    )
    rule_specs = _apply_rule_weight_overrides(rule_specs, RULE_WEIGHTS_BY_NAME)
    rule_summary = summarize_rule_specs(rule_specs)
    print(
        "catalog: "
        f"specs={rule_summary['num_specs']} "
        f"relation_tests={rule_summary['num_relation_tests']} "
        f"over_T_transforms={rule_summary['num_over_T_transforms']} "
        f"by_category={rule_summary['by_category']}"
    )
    print(
        "composite_loss_weights: "
        f"supervised_weight={COMPOSITE_GLOBAL_WEIGHTS['supervised_weight']} "
        f"relation_constraint_weight={COMPOSITE_GLOBAL_WEIGHTS['relation_constraint_weight']} "
        f"worst_case_over_T_weight={COMPOSITE_GLOBAL_WEIGHTS['worst_case_over_T_weight']} "
        f"target_mapped_weight={COMPOSITE_GLOBAL_WEIGHTS['target_mapped_weight']}"
    )
    _print_rule_weights(rule_specs)

    assignment_probe = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_weight=0.0,
        relation_constraint_weight=0.0,
        worst_case_over_T_weight=0.0,
    )
    relation_constraints = assignment_probe.assigned_relation_constraints
    over_T_transform_set = assignment_probe.assigned_over_T_transform_set
    print(
        "catalog_assignment(exclusive): "
        f"relation_constraints={assignment_probe.rule_assignment_summary['assigned_relation_constraints']} "
        f"over_T_transforms={assignment_probe.rule_assignment_summary['assigned_over_T_transforms']} "
        f"fallback_to_relation={assignment_probe.rule_assignment_summary['fallback_to_relation']} "
        f"fallback_to_over_T={assignment_probe.rule_assignment_summary['fallback_to_over_T']} "
        f"dropped={assignment_probe.rule_assignment_summary['dropped_rule_specs']}"
    )
    if COMPOSITE_GLOBAL_WEIGHTS["worst_case_over_T_weight"] > 0 and len(over_T_transform_set) == 0:
        print(
            "WARNING: worst_case_over_T_loss is enabled but over_T_transform_set is empty; "
            "the composite loss will reduce to supervised-only for this branch."
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
        supervised_weight=COMPOSITE_GLOBAL_WEIGHTS["supervised_weight"],
        relation_constraint_weight=COMPOSITE_GLOBAL_WEIGHTS["relation_constraint_weight"],
        worst_case_over_T_weight=COMPOSITE_GLOBAL_WEIGHTS["worst_case_over_T_weight"],
        target_mapped_weight=COMPOSITE_GLOBAL_WEIGHTS["target_mapped_weight"],
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

    results: dict[str, ModelResult] = {
        "baseline": baseline_result,
        "composite": composite_result,
    }

    for result in results.values():
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

    return {
        "dataset": {
            "train_jsonl": str(args.train_jsonl),
            "train_md": str(args.train_md),
            "test_jsonl": str(args.test_jsonl),
            "test_md": str(args.test_md),
        },
        "headers_match": {
            "train": train_headers_match,
            "test": test_headers_match,
            "train_test_schema_match": schema_match,
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
            "assignment_summary": assignment_probe.rule_assignment_summary,
            "transform_consistency_report": transform_consistency_report,
        },
        "results": {
            mode: {
                "test_metrics": result.test_metrics,
                "last_epoch": result.train_history[-1] if result.train_history else None,
                "violation_report": result.violation_report,
                "worst_case_over_T_report": result.worst_case_over_T_report,
            }
            for mode, result in results.items()
        },
        "deltas_vs_baseline": deltas_vs_baseline,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Compare baseline KAN vs composite metamorphic training for synthetic audit "
            "with separate train and test datasets."
        )
    )
    parser.add_argument(
        "--train-jsonl",
        type=Path,
        default=ROOT / "data" / "synthetic" / "audit_train_y.jsonl",
        help="Path to train JSONL",
    )
    parser.add_argument(
        "--train-md",
        type=Path,
        default=ROOT / "data" / "synthetic" / "audit_train_y.md",
        help="Path to train metadata .md",
    )
    parser.add_argument(
        "--test-jsonl",
        type=Path,
        default=ROOT / "data" / "synthetic" / "audit_test_shift_y.jsonl",
        help="Path to test JSONL",
    )
    parser.add_argument(
        "--test-md",
        type=Path,
        default=ROOT / "data" / "synthetic" / "audit_test_shift_y.md",
        help="Path to test metadata .md",
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--train-val-ratio",
        type=float,
        default=0.20,
        help="Fraction of train dataset reserved for validation",
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
        "--train-limit",
        type=int,
        default=None,
        help="Optional cap on number of training samples loaded after the header",
    )
    parser.add_argument(
        "--test-limit",
        type=int,
        default=None,
        help="Optional cap on number of test samples loaded after the header",
    )
    parser.add_argument(
        "--include-target-mapped-over-T-transforms",
        dest="include_target_mapped_over_t_transforms",
        action="store_true",
        default=True,
        help="Include optional over-T target-mapped transforms for area scaling",
    )
    parser.add_argument(
        "--disable-target-mapped-over-T-transforms",
        dest="include_target_mapped_over_t_transforms",
        action="store_false",
        help="Disable optional over-T target-mapped transforms for area scaling",
    )
    parser.add_argument(
        "--print-json",
        action="store_true",
        help="Print summary as JSON after the human-readable report",
    )
    args = parser.parse_args()

    report = run_comparison(args)
    if args.print_json:
        print("\n[JSON]")
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
