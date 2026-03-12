from __future__ import annotations

import argparse
import csv
import math
import os
import sys
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import torch

ROOT = Path(__file__).resolve().parents[3]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
PROJECT_ROOT = ROOT.parent
RUNTIME_TRAINER_ROOT = PROJECT_ROOT / "runtime.trainer"
if str(RUNTIME_TRAINER_ROOT) not in sys.path:
    sys.path.insert(0, str(RUNTIME_TRAINER_ROOT))

import Device
from trainer.solarPlant.alternative.AlternativeKanTrainer import AlternativeKanTrainer

TIME_FEATURE_NAMES = [
    "month_sin",
    "month_cos",
    "day_sin",
    "day_cos",
    "hour_sin",
    "hour_cos",
    "quarter_sin",
    "quarter_cos",
]

ALL_NUMERIC_COLUMNS = [
    "cellTemperature",
    "Infecar.temperature",
    "Infecar.radiation",
    "generatedReactivePower",
    "generatedActivePower",
    "consumedReactivePower",
    "consumedActivePower",
]

TARGET_COLUMN = "generatedActivePower"
FORBIDDEN_INPUTS = {"generatedReactivePower", TARGET_COLUMN}
INPUT_NUMERIC_COLUMNS = [c for c in ALL_NUMERIC_COLUMNS if c not in FORBIDDEN_INPUTS]
TARGET_FUTURE_COLUMN = "generatedActivePower_future"


def parse_utc_instant(value: str) -> datetime:
    ts = value.strip()
    if ts.endswith("Z"):
        ts = ts[:-1] + "+00:00"
    dt = datetime.fromisoformat(ts)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def sin_cos(value: float, period: float) -> tuple[float, float]:
    angle = (2.0 * math.pi * value) / period
    return math.sin(angle), math.cos(angle)


def encode_time_features(dt_utc: datetime) -> list[float]:
    month_idx = dt_utc.month - 1
    day_idx = dt_utc.day - 1
    hour_idx = dt_utc.hour
    quarter_idx = (dt_utc.month - 1) // 3  # year quarter: 0..3

    month_sin, month_cos = sin_cos(month_idx, 12.0)
    day_sin, day_cos = sin_cos(day_idx, 31.0)
    hour_sin, hour_cos = sin_cos(hour_idx, 24.0)
    quarter_sin, quarter_cos = sin_cos(quarter_idx, 4.0)

    return [
        month_sin,
        month_cos,
        day_sin,
        day_cos,
        hour_sin,
        hour_cos,
        quarter_sin,
        quarter_cos,
    ]


def load_hourly_means(csv_path: Path) -> list[dict]:
    def make_bucket() -> dict[str, float]:
        bucket = {"count": 0.0}
        for col in ALL_NUMERIC_COLUMNS:
            bucket[col] = 0.0
        return bucket

    buckets: dict[datetime, dict[str, float]] = defaultdict(make_bucket)

    with csv_path.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        required = {"instant", *ALL_NUMERIC_COLUMNS}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"CSV missing required columns: {sorted(missing)}")

        for row in reader:
            dt = parse_utc_instant(row["instant"])
            hour_dt = dt.replace(minute=0, second=0, microsecond=0)
            bucket = buckets[hour_dt]
            try:
                parsed_values = {col: float(row[col]) for col in ALL_NUMERIC_COLUMNS}
            except (TypeError, ValueError):
                continue
            try:
                for col in ALL_NUMERIC_COLUMNS:
                    bucket[col] += parsed_values[col]
            except (TypeError, ValueError):
                continue
            bucket["count"] += 1.0

    hourly_rows: list[dict] = []
    for hour_dt in sorted(buckets.keys()):
        bucket = buckets[hour_dt]
        count = int(bucket.get("count", 0.0))
        if count <= 0:
            continue
        row = {"instant": hour_dt}
        for col in ALL_NUMERIC_COLUMNS:
            row[col] = float(bucket[col] / count)
        hourly_rows.append(row)
    return hourly_rows


def build_horizon_examples(hourly_rows: list[dict], horizon_hours: int) -> list[dict]:
    if horizon_hours <= 0:
        raise ValueError("horizon_hours must be > 0")
    by_instant = {row["instant"]: row for row in hourly_rows}
    examples: list[dict] = []
    for row in hourly_rows:
        dst_dt = row["instant"] + timedelta(hours=int(horizon_hours))
        target_row = by_instant.get(dst_dt)
        if target_row is None:
            continue
        enriched = dict(row)
        enriched[TARGET_FUTURE_COLUMN] = float(target_row[TARGET_COLUMN])
        examples.append(enriched)
    return examples


def split_records(
        records: list[dict],
        seed: int,
        train_ratio: float,
        val_ratio: float,
        test_ratio: float,
) -> tuple[list[dict], list[dict], list[dict]]:
    if len(records) < 10:
        raise ValueError(f"Need >=10 records after hourly aggregation, got {len(records)}")
    if not math.isclose(train_ratio + val_ratio + test_ratio, 1.0, rel_tol=1e-6, abs_tol=1e-6):
        raise ValueError("train_ratio + val_ratio + test_ratio must be 1.0")

    rng = np.random.default_rng(seed)
    indices = np.arange(len(records))
    rng.shuffle(indices)

    train_end = max(1, int(len(records) * train_ratio))
    val_end = min(len(records) - 1, train_end + max(1, int(len(records) * val_ratio)))
    if train_end >= val_end:
        train_end = max(1, val_end - 1)

    train_idx = indices[:train_end]
    val_idx = indices[train_end:val_end]
    test_idx = indices[val_end:]
    if len(test_idx) == 0:
        test_idx = indices[-1:]
        val_idx = indices[train_end:-1]
        if len(val_idx) == 0:
            val_idx = indices[train_end - 1: train_end]
            train_idx = indices[: train_end - 1]

    train_rows = [records[int(i)] for i in train_idx]
    val_rows = [records[int(i)] for i in val_idx]
    test_rows = [records[int(i)] for i in test_idx]
    return train_rows, val_rows, test_rows


def compute_feature_stats(train_rows: list[dict]) -> tuple[list[float], list[float]]:
    feature_matrix = np.array(
        [[float(row[col]) for col in INPUT_NUMERIC_COLUMNS] for row in train_rows],
        dtype=np.float64,
    )
    means = feature_matrix.mean(axis=0)
    stds = feature_matrix.std(axis=0)
    stds = np.where(stds <= 1e-12, 1.0, stds)
    return means.astype(np.float32).tolist(), stds.astype(np.float32).tolist()


def compute_target_scaler(train_rows: list[dict]) -> tuple[float, float]:
    targets = [float(row[TARGET_FUTURE_COLUMN]) for row in train_rows]
    out_min = float(min(targets))
    out_max = float(max(targets))
    if out_max <= out_min:
        out_max = out_min + 1.0
    return out_min, out_max


def make_kan_items(rows: list[dict], out_min: float, out_max: float) -> list[dict]:
    span = out_max - out_min
    items: list[dict] = []
    for row in rows:
        t_features = encode_time_features(row["instant"])
        numerical_t_features = [float(row[col]) for col in INPUT_NUMERIC_COLUMNS]
        target_raw = float(row[TARGET_FUTURE_COLUMN])
        out = (target_raw - out_min) / span
        items.append(
            {
                "out": float(out),
                "t": [float(v) for v in t_features],
                "categorical_t_features": [],
                "numerical_t_features": [float(v) for v in numerical_t_features],
                "lookback_t": [],
                "categorical_lookback_features": [],
                "numerical_lookback_features": [],
            }
        )
    return items


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Train KAN directly from infecar.csv (hourly mean aggregation), "
            "target=generatedActivePower, excluding generatedReactivePower from inputs."
        )
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=PROJECT_ROOT / "runtime.test" / "data" / "infecar.csv",
        help="Path to infecar.csv",
    )
    parser.add_argument(
        "--model-out",
        type=Path,
        default=PROJECT_ROOT / "temp" / "test-models-alternative" / "SolarPlant" / "generatedActivePower_h24_from_csv.bin",
        help="Where to save trained model weights",
    )
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=5e-4)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--horizon-hours", type=int, default=24)
    parser.add_argument("--train-ratio", type=float, default=0.64)
    parser.add_argument("--val-ratio", type=float, default=0.16)
    parser.add_argument("--test-ratio", type=float, default=0.20)
    parser.add_argument(
        "--limit-hours",
        type=int,
        default=None,
        help="Optional cap on number of hourly rows after aggregation (for quick tests)",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()

    csv_path = args.csv.resolve()
    if not csv_path.exists():
        raise FileNotFoundError(f"CSV not found: {csv_path}")

    hourly_rows = load_hourly_means(csv_path)
    if args.limit_hours is not None:
        hourly_rows = hourly_rows[: int(args.limit_hours)]
    if len(hourly_rows) < 10:
        raise ValueError(f"Need >=10 hourly rows, got {len(hourly_rows)}")
    horizon_examples = build_horizon_examples(hourly_rows, horizon_hours=int(args.horizon_hours))
    if len(horizon_examples) < 10:
        raise ValueError(
            f"Need >=10 horizon examples for +{args.horizon_hours}h forecast, got {len(horizon_examples)}"
        )

    train_rows, val_rows, test_rows = split_records(
        records=horizon_examples,
        seed=int(args.seed),
        train_ratio=float(args.train_ratio),
        val_ratio=float(args.val_ratio),
        test_ratio=float(args.test_ratio),
    )
    means, stds = compute_feature_stats(train_rows)
    out_min, out_max = compute_target_scaler(train_rows)
    train_items = make_kan_items(train_rows, out_min=out_min, out_max=out_max)
    val_items = make_kan_items(val_rows, out_min=out_min, out_max=out_max)
    test_items = make_kan_items(test_rows, out_min=out_min, out_max=out_max)

    input_variables = list(TIME_FEATURE_NAMES) + list(INPUT_NUMERIC_COLUMNS)
    lookback = 0
    device = Device.get_device()

    print(
        "config: "
        f"csv={csv_path} "
        f"horizon_hours={args.horizon_hours} "
        f"epochs={args.epochs} "
        f"batch_size={args.batch_size} "
        f"lr={args.lr} "
        f"seed={args.seed} "
        f"device={device}",
        flush=True,
    )
    print(
        "data: "
        f"hourly_rows={len(hourly_rows)} "
        f"horizon_examples={len(horizon_examples)} "
        f"train={len(train_rows)} "
        f"val={len(val_rows)} "
        f"test={len(test_rows)}",
        flush=True,
    )
    print(
        "features: "
        f"time={TIME_FEATURE_NAMES} "
        f"numerical_inputs={INPUT_NUMERIC_COLUMNS} "
        f"excluded={sorted(FORBIDDEN_INPUTS)} "
        f"target={TARGET_COLUMN}+{args.horizon_hours}h",
        flush=True,
    )
    print(
        "target_scaler: "
        f"out_min={out_min:.6f} "
        f"out_max={out_max:.6f}",
        flush=True,
    )

    trainer = AlternativeKanTrainer(
        name="SolarPlantAlternative",
        input_variables=input_variables,
        output_variable=f"{TARGET_COLUMN}+{args.horizon_hours}h",
        lookback=lookback,
        means=means,
        stds=stds,
        out_min=out_min,
        out_max=out_max,
        batch_size=int(args.batch_size),
        epochs=int(args.epochs),
        device=device,
        lr=float(args.lr),
        seed=int(args.seed),
    )
    model, best_val_metrics = trainer.train(train_items, val_items)
    test_metrics = trainer.evaluate(model, test_items)

    model_out = args.model_out.resolve()
    os.makedirs(model_out.parent, exist_ok=True)
    torch.save(model.state_dict(), model_out)

    print(
        "best_val: "
        f"n={best_val_metrics['n_samples']} "
        f"mae_model={float(best_val_metrics['mae_model']):.6f} "
        f"rmse_model={float(best_val_metrics['rmse_model']):.6f} "
        f"r2={float(best_val_metrics['r2']):.6f} "
        f"mae_raw={float(best_val_metrics['mae_raw']):.6f} "
        f"rmse_raw={float(best_val_metrics['rmse_raw']):.6f}",
        flush=True,
    )
    print(
        "test: "
        f"n={test_metrics['n_samples']} "
        f"mae_model={float(test_metrics['mae_model']):.6f} "
        f"rmse_model={float(test_metrics['rmse_model']):.6f} "
        f"r2={float(test_metrics['r2']):.6f} "
        f"mae_raw={float(test_metrics['mae_raw']):.6f} "
        f"rmse_raw={float(test_metrics['rmse_raw']):.6f}",
        flush=True,
    )
    print(f"model_saved={model_out}", flush=True)


if __name__ == "__main__":
    main()
