# Picota Training API Contract

## Endpoints

- `POST /api/v1/cases`
    - Crea un `case`.
- `DELETE /api/v1/cases/{case_id}`
    - Elimina un `case` con su contenido.
- `POST /api/v1/cases/{case_id}/datasets`
    - Incorpora un dataset al `case` copiándolo desde `source_path`.
- `GET /api/v1/cases/{case_id}/datasets`
    - Lista datasets incorporados al `case`.
- `POST /api/v1/cases/{case_id}/trainings`
    - Lanza un training job asociado al `case` usando `data_source.dataset_id`.
- `POST /api/v1/trainings`
    - Starts an asynchronous training job (legacy path-based mode).
    - Returns a persistent `ticket_id`.
- `GET /api/v1/trainings/{ticket_id}`
    - Returns job state (`queued`, `running`, `completed`, `failed`) and final result/error.
- `POST /api/v1/inferences`
    - Executes inference with a previously trained model from a completed training ticket.
    - Returns predictions for the provided input instances.

## Request Model (POST /api/v1/cases/{case_id}/trainings)

```json
{
  "job_name": "training-h24",
  "created_by": "qa-user",
  "data_source": {
    "kind": "tabular_timeseries",
    "dataset_id": "dataset-main",
    "limit_rows": 4000,
    "options": {
      "case_name": "case-a",
      "timestamp_column": "instant",
      "target_column": "target",
      "time_bucket": "hour",
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
    - In case-centric mode uses `data_source.dataset_id` (resolved from case workspace).
    - Legacy mode can still use `data_source.path`.
    - Uses `data_source.options` to describe schema, bucketing and scaling.
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

- `case_name` (optional): etiqueta lógica del caso de uso; si no se informa, usa `job_name`.
- `timestamp_column`: columna temporal.
- `target_column`: columna objetivo en el dataset.
- `time_bucket`: `none` | `hour` | `day` (define bucket temporal y la codificación temporal interna).
- `time_features` ya no se admite en la API.
- `entity_key_columns` (optional): columnas que identifican entidad para emparejar horizonte.
- `numerical_input_columns` (optional): si no se define, se infiere.
- `categorical_input_columns` (optional): si no se define, se infiere.
- `exclude_input_columns` (optional): columnas a excluir de features.
- `numerical_scaler`: `zscore` | `minmax` | `none`.
- `categorical_encoding`: `one_hot` | `none`.
- `job_name` del request identifica la ejecución de entrenamiento y se devuelve en `result.job_name`.

## Case & Dataset APIs

- `POST /api/v1/cases` body:
    - `case_id`
- `POST /api/v1/cases/{case_id}/datasets` body:
    - `source_path` (ruta absoluta del fichero original)
    - `dataset_id` (opcional)
- `GET /api/v1/cases/{case_id}/datasets`:
    - devuelve metadatos y ruta almacenada de cada dataset

## Ticket Persistence

- The service is configured with one `workspace` directory.
- Inside workspace:
    - one directory per case: `<workspace>/<case_id>/`
    - datasets at: `<workspace>/<case_id>/datasets/`
    - tickets at: `<workspace>/<case_id>/tickets/<ticket_id>/`
- Each ticket directory contains:
    - `request.json` with training configuration
    - `result.json` with state/result payload returned by `GET /api/v1/trainings/{ticket_id}`
    - serialized model artifact (`model.pt`)

## Inference Request Model (POST /api/v1/inferences)

```json
{
  "training_ticket_id": "1b9e99d91b2348259463c8f0051458b7",
  "output_scale": "both",
  "instances": [
    {
      "variables": {
        "month_sin": 0.0,
        "month_cos": 1.0,
        "day_sin": 0.0,
        "day_cos": 1.0,
        "hour_sin": 0.5,
        "hour_cos": 0.86,
        "quarter_sin": 0.0,
        "quarter_cos": 1.0,
        "feature_1": 12.3,
        "feature_2": 5.5,
        "feature_3": 0.4
      }
    }
  ]
}
```

- `training_ticket_id` must reference a `completed` training ticket.
- `output_scale`: `raw` | `normalized` | `both` (default: `raw`).
- `instances`:
    - Recommended format: `variables` object with key/value pairs using names from `result.input_variables`.
    - Alternative format: internal vectors (`t`, `numerical_t_features`, `categorical_t_features`, `lookback_t`,
      `numerical_lookback_features`, `categorical_lookback_features`).
