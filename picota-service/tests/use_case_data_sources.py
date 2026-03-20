from __future__ import annotations

from pathlib import Path


def energigran_data_source(repo_root: Path, *, limit_rows: int | None = None) -> dict:
    payload = {
        "kind": "tabular_timeseries",
        "path": str(repo_root / "runtime.test" / "data" / "energigran.tsv"),
        "options": {
            "case_name": "energigran",
            "timestamp_column": "instant",
            "target_column": "generation",
            "time_bucket": "hour",
            "time_features": "hourly",
            "entity_key_columns": [],
            "numerical_input_columns": [
                "cellTemperature",
                "Infecar.temperature",
                "Infecar.radiation",
                "grid",
                "consumption",
            ],
            "categorical_input_columns": [],
            "numerical_scaler": "zscore",
            "categorical_encoding": "none",
        },
    }
    if limit_rows is not None:
        payload["limit_rows"] = int(limit_rows)
    return payload


def europlatano_data_source(repo_root: Path, *, limit_rows: int | None = None) -> dict:
    payload = {
        "kind": "tabular_timeseries",
        "path": str(repo_root / "runtime.test" / "data" / "europlatano.tsv"),
        "options": {
            "case_name": "europlatano",
            "timestamp_column": "instant",
            "target_column": "Production",
            "time_bucket": "day",
            "time_features": "daily",
            "entity_key_columns": ["Category", "Island", "Area", "Altitude"],
            "numerical_scaler": "minmax",
            "categorical_encoding": "one_hot",
        },
    }
    if limit_rows is not None:
        payload["limit_rows"] = int(limit_rows)
    return payload
