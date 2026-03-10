import argparse
import json
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

import torch

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from kan.DatasetLoader import DatasetLoader
from kan.MetamorphicCatalog import (
    summarize_rule_specs,
)
from kan.MetamorphicLoss import CompositeMetamorphicLoss
from trainer.spanishHomes.metamorphic_rules import build_spanish_homes_monetary_spending_rule_specs
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
    compute_violation_report,
    evaluate_worst_case_over_T,
    validate_metamorphic_transforms_on_batch,
)
import Device


def _normalize_household(household: str) -> str:
    value = household.strip().lower()
    if value.startswith("hogar"):
        value = value[len("hogar"):]
    if not value.isdigit():
        raise ValueError(f"Invalid household identifier: {household!r}")
    return value.zfill(2)


def _split_stem(stem: str) -> tuple[str, str]:
    if "_" not in stem:
        raise ValueError(f"Invalid dataset stem '{stem}', expected format hogarXX_target")
    household, target = stem.split("_", 1)
    return household, target


def _resolve_dataset_paths(args) -> tuple[Path, Path]:
    if (args.jsonl is None) != (args.md is None):
        raise ValueError("Use both --jsonl and --md together, or neither to resolve from --household/--target.")
    if args.jsonl is not None and args.md is not None:
        return args.jsonl, args.md

    household = _normalize_household(args.household)
    stem = f"hogar{household}_{args.target}"
    jsonl_path = args.data_dir / f"{stem}.jsonl"
    md_path = args.data_dir / f"{stem}.md"
    if not jsonl_path.exists():
        raise FileNotFoundError(f"Dataset not found: {jsonl_path}")
    if not md_path.exists():
        raise FileNotFoundError(f"Dataset metadata not found: {md_path}")
    return jsonl_path, md_path


def _list_targets(data_dir: Path) -> list[str]:
    targets = set()
    for path in sorted(data_dir.glob("hogar*_*.jsonl")):
        stem = path.stem
        if "_" not in stem:
            continue
        targets.add(stem.split("_", 1)[1])
    return sorted(targets)


def _discover_dataset_pairs(data_dir: Path) -> list[tuple[Path, Path]]:
    pairs: list[tuple[Path, Path]] = []
    for jsonl_path in sorted(data_dir.glob("hogar*_*.jsonl")):
        md_path = jsonl_path.with_suffix(".md")
        if not md_path.exists():
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
            "overall_violation_rate")
        if isinstance(baseline_vr, (int, float)) and math.isfinite(float(baseline_vr)):
            baseline_violation_rates.append(float(baseline_vr))
        composite_vr = report.get("results", {}).get("composite", {}).get("violation_report", {}).get(
            "overall_violation_rate")
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


def run_single_comparison(jsonl_path: Path, md_path: Path, args, print_details: bool = True) -> dict:
    if print_details:
        print(f"dataset_jsonl={jsonl_path}")
        print(f"dataset_md={md_path}")

    jsonl_header, md_header = load_headers(jsonl_path, md_path)
    headers_match = jsonl_header == md_header
    if print_details:
        print(f"metadata_header_match(jsonl,md)={headers_match}")
    if print_details and not headers_match:
        print("WARNING: headers differ between .jsonl and .md")

    loader = DatasetLoader(str(jsonl_path))
    items = loader.load()
    if args.limit is not None:
        items = items[: args.limit]
    if len(items) < 10:
        raise ValueError(f"Need more samples for comparison, got {len(items)}")

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
        _, numerical_t_feature_names, _ = infer_feature_layout(
            input_variables=input_variables,
            sample_batch=sample_batch_for_shape,
        )

    rule_specs = build_spanish_homes_monetary_spending_rule_specs(
        numerical_t_feature_names=numerical_t_feature_names,
        include_target_mapped=args.include_target_mapped_over_T_transforms,
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

    assignment_probe = CompositeMetamorphicLoss.from_rule_specs(
        rule_specs=rule_specs,
        supervised_weight=0.0,
        relation_constraint_weight=0.0,
        worst_case_over_T_weight=0.0,
    )
    relation_constraints = assignment_probe.assigned_relation_constraints
    over_T_transform_set = assignment_probe.assigned_over_T_transform_set
    if print_details:
        print(
            "catalog_assignment(exclusive): "
            f"relation_constraints={assignment_probe.rule_assignment_summary['assigned_relation_constraints']} "
            f"over_T_transforms={assignment_probe.rule_assignment_summary['assigned_over_T_transforms']} "
            f"fallback_to_relation={assignment_probe.rule_assignment_summary['fallback_to_relation']} "
            f"fallback_to_over_T={assignment_probe.rule_assignment_summary['fallback_to_over_T']} "
            f"dropped={assignment_probe.rule_assignment_summary['dropped_rule_specs']}"
        )
    if print_details and args.worst_case_over_T_weight > 0 and len(over_T_transform_set) == 0:
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
        if relation_constraints:
            result.violation_report = compute_violation_report(
                result.model,
                make_loader(split.test_items, args.batch_size, shuffle=False),
                metamorphic_tests=relation_constraints,
            )

    if print_details:
        print_report("Baseline (MSE)", baseline_result)
        print_report("Composite Metamorphic", composite_result)

    delta = compute_delta_against_baseline(composite_result, baseline_result, prefix="composite")
    if print_details:
        print("\n[Delta composite - baseline]")
        for key, value in delta.items():
            print(f"{key}={value:.6f}")

    household, target = _split_stem(jsonl_path.stem)
    payload_results = {}
    for mode_name, result in results.items():
        payload_results[mode_name] = {
            "test_metrics": result.test_metrics,
            "last_epoch": result.train_history[-1] if result.train_history else None,
            "violation_report": result.violation_report,
            "worst_case_over_T_report": result.worst_case_over_T_report,
        }
    payload = {
        "dataset": {
            "id": jsonl_path.stem,
            "household": household,
            "target": target,
            "jsonl": str(jsonl_path),
            "md": str(md_path),
        },
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
        "deltas_vs_baseline": {"composite": delta},
    }
    return payload


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Compare baseline vs composite metamorphic training on Spanish homes monetary spending."
    )
    parser.add_argument(
        "--data-dir",
        type=Path,
        default=ROOT / "data" / "spanish_homes" / "data",
        help="Directory with spanish_homes jsonl/md files",
    )
    parser.add_argument(
        "--household",
        type=str,
        default="01",
        help="Household id (e.g. 01 or hogar01) when --jsonl/--md are not passed",
    )
    parser.add_argument(
        "--target",
        type=str,
        default="gastoMonetario:productosAlimenticios11",
        help="Target suffix in filename after '<household>_'",
    )
    parser.add_argument("--jsonl", type=Path, default=None, help="Optional explicit dataset .jsonl path")
    parser.add_argument("--md", type=Path, default=None, help="Optional explicit dataset .md path")
    parser.add_argument("--list-targets", action="store_true", help="List available target suffixes and exit")
    parser.add_argument(
        "--all-datasets",
        action="store_true",
        help="Process all hogar*_*.jsonl + .md pairs found in --data-dir",
    )
    parser.add_argument(
        "--max-datasets",
        type=int,
        default=None,
        help="Optional cap for --all-datasets mode (for quick smoke runs)",
    )
    parser.add_argument(
        "--report-file",
        type=Path,
        default=None,
        help="Write comparison report JSON to this file",
    )
    parser.add_argument("--fail-fast", action="store_true", help="Stop batch processing on first dataset error")

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
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Include target-mapped over-T transform derived from coupled members/income scaling rule",
    )
    parser.add_argument(
        "--print-json",
        action="store_true",
        help="Print JSON payload to stdout",
    )
    return parser


def main():
    parser = _build_parser()
    args = parser.parse_args()

    if args.list_targets:
        targets = _list_targets(args.data_dir)
        print(f"available_targets={len(targets)}")
        for target in targets:
            print(target)
        return

    if args.all_datasets and (args.jsonl is not None or args.md is not None):
        raise ValueError("--all-datasets cannot be used together with --jsonl/--md")

    if args.all_datasets:
        dataset_pairs = _discover_dataset_pairs(args.data_dir)
        if args.max_datasets is not None:
            dataset_pairs = dataset_pairs[: args.max_datasets]
        if not dataset_pairs:
            raise ValueError(f"No hogar*_*.jsonl/.md pairs found in {args.data_dir}")

        success_reports = []
        failures = []
        total = len(dataset_pairs)
        print(f"batch_mode=all_datasets total={total}")
        for index, (jsonl_path, md_path) in enumerate(dataset_pairs, start=1):
            dataset_id = jsonl_path.stem
            try:
                report = run_single_comparison(jsonl_path, md_path, args, print_details=False)
                success_reports.append(report)
                delta_mae = report["deltas_vs_baseline"]["composite"].get("delta_mae_composite_minus_base",
                                                                          float("nan"))
                print(f"[{index}/{total}] OK {dataset_id} delta_mae={delta_mae:.6f}")
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
                "relation_constraint_weight": args.relation_constraint_weight,
                "worst_case_over_T_weight": args.worst_case_over_T_weight,
                "target_mapped_weight": args.target_mapped_weight,
                "include_target_mapped_over_T_transforms": args.include_target_mapped_over_T_transforms,
                "limit": args.limit,
            },
        }

        report_file = args.report_file or (args.data_dir / "comparison_all_houses_report.json")
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
    payload = run_single_comparison(jsonl_path, md_path, args, print_details=True)
    if args.report_file is not None:
        args.report_file.parent.mkdir(parents=True, exist_ok=True)
        args.report_file.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        print(f"report_file={args.report_file}")
    if args.print_json:
        print("\n[JSON]")
        print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
