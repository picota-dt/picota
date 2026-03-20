# Picota Training API Contract

## Endpoints

- `POST /api/v1/trainings`
    - Starts an asynchronous training job.
    - Returns a persistent `ticket_id`.
- `GET /api/v1/trainings/{ticket_id}`
    - Returns job state (`queued`, `running`, `completed`, `failed`) and final result/error.

## Request Model (POST)

```json
{
  "job_name": "training-h24",
  "created_by": "qa-user",
  "data_source": {
    "kind": "tabular_timeseries",
    "path": "/abs/path/to/dataset.tsv",
    "limit_rows": 4000,
    "options": {
      "case_name": "case-a",
      "timestamp_column": "instant",
      "target_column": "target",
      "time_bucket": "hour",
      "time_features": "hourly",
      "entity_key_columns": [],
      "numerical_input_columns": [
        "feature_1",
        "feature_2"
      ],
      "categorical_input_columns": [
        "zone"
      ],
      "numerical_scaler": "zscore",
      "categorical_encoding": "one_hot"
    }
  },
  "target_variable": "target",
  "lookback": 0,
  "time_horizon": {
    "value": 24,
    "unit": "hours"
  },
  "split": {
    "train_ratio": 0.64,
    "val_ratio": 0.16,
    "test_ratio": 0.20
  },
  "architecture": {
    "family": "kan",
    "mode": "baseline",
    "epochs": 10,
    "batch_size": 64,
    "learning_rate": 0.0005,
    "seed": 42,
    "tabnet_n_steps": 4,
    "tabnet_n_d": 24,
    "tabnet_n_a": 24,
    "tabnet_gamma": 1.3,
    "tabnet_dropout": 0.05,
    "tabnet_mask_temperature": 1.0
  },
  "metamorphic": {
    "enabled": false,
    "supervised_weight": 1.0,
    "relation_constraint_weight": 0.25,
    "worst_case_over_t_weight": 0.0,
    "violation_atol": 1e-6,
    "violation_rtol": 1e-4,
    "rule_specs": [
      {
        "name": "radiation_up_implies_active_power_non_decreasing",
        "kind": "relation",
        "category": "directional_ordinal",
        "relation": "greater_or_equal",
        "weight": 1.0,
        "transforms": [
          {
            "op": "add",
            "field": "numerical_t_features",
            "feature": "Infecar.radiation",
            "delta": 25.0
          }
        ]
      }
    ]
  },
  "output_dir": "/abs/path/for/model-artifacts"
}
```

## Supported Data Sources

- `tabular_timeseries`
    - Generic adapter for tabular time-series datasets.
    - Uses `data_source.options` to describe schema, bucketing, feature encoding and scaling.
    - Use-case-specific setup (columns, entity keys, etc.) should live outside the framework.

## Training Modes

- `architecture.mode = "baseline"`
    - Standard supervised training (KAN or TabNet, depending on `architecture.family`).
- `architecture.mode = "metamorphic"` (or `metamorphic.enabled = true`)
    - Composite metamorphic loss is enabled for KAN o TabNet cuando `metamorphic.rule_specs` tenga reglas.

## Supported Architecture Families

- `kan`
- `tabnet`

## Rule Spec DSL

- `metamorphic.rule_specs[].kind`: `relation` | `over_t`
- `metamorphic.rule_specs[].category`: `invariance` | `directional_ordinal` | `target_mapped`
- `metamorphic.rule_specs[].relation` (si `kind=relation`): `equal`, `greater`, `greater_or_equal`, `lower`,
  `lower_or_equal`, `monotonic`, `proportional`
- `metamorphic.rule_specs[].transforms[]`:
    - `op`: `add`, `scale`, `scale_if_positive`, `noise`, `zero`
    - `field`: `t`, `numerical_t_features`, `categorical_t_features`, `lookback_t`, `numerical_lookback_features`,
      `categorical_lookback_features`
    - Selección de feature por `index` o por `feature` (nombre).

## Adapter Options (`data_source.options`)

- `case_name` (optional): nombre lógico que se devuelve en `result.case_name`.
- `timestamp_column`: columna temporal.
- `target_column`: columna objetivo en el dataset.
- `time_bucket`: `none` | `hour` | `day`.
- `time_features`: `none` | `hourly` | `daily`.
- `entity_key_columns` (optional): columnas que identifican entidad para emparejar horizonte.
- `numerical_input_columns` (optional): si no se define, se infiere.
- `categorical_input_columns` (optional): si no se define, se infiere.
- `exclude_input_columns` (optional): columnas a excluir de features.
- `numerical_scaler`: `zscore` | `minmax` | `none`.
- `categorical_encoding`: `one_hot` | `none`.

## Ticket Persistence

- One JSON file per ticket under configured tickets directory.
- Each ticket stores:
    - request payload
    - current status and status history
    - result metrics and model path when completed
    - error details and traceback when failed
