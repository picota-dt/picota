from __future__ import annotations


def energigran_rule_specs() -> list[dict]:
    return [
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
                    "delta": 25.0,
                }
            ],
        },
        {
            "name": "cell_temperature_up_tends_to_reduce_efficiency",
            "kind": "relation",
            "category": "directional_ordinal",
            "relation": "monotonic",
            "direction": "decreasing",
            "weight": 0.3,
            "transforms": [
                {
                    "op": "add",
                    "field": "numerical_t_features",
                    "feature": "cellTemperature",
                    "delta": 1.0,
                }
            ],
        },
        {
            "name": "consumption_constant_with_grid_import_decreasing_generation_increases",
            "kind": "relation",
            "category": "directional_ordinal",
            "relation": "greater",
            "weight": 1.0,
            "transforms": [
                {
                    "op": "scale_if_positive",
                    "field": "numerical_t_features",
                    "feature": "grid",
                    "factor": 0.90,
                }
            ],
        },
    ]


def europlatano_rule_specs() -> list[dict]:
    return [
        {
            "name": "production_proportional_to_area",
            "kind": "relation",
            "category": "target_mapped",
            "relation": "proportional",
            "factor": 1.20,
            "weight": 1.0,
            "violation_atol": 0.0,
            "violation_rtol": 0.17,
            "transforms": [
                {
                    "op": "scale",
                    "field": "numerical_t_features",
                    "feature": "Area",
                    "factor": 1.20,
                }
            ],
        },
        {
            "name": "production_non_decreasing_with_rainfall",
            "kind": "relation",
            "category": "directional_ordinal",
            "relation": "greater_or_equal",
            "weight": 1.0,
            "transforms": [
                {
                    "op": "scale",
                    "field": "numerical_t_features",
                    "feature": "Territory.Precipitation",
                    "factor": 1.10,
                }
            ],
        },
        {
            "name": "production_robust_to_humidity_noise",
            "kind": "relation",
            "category": "invariance",
            "relation": "equal",
            "weight": 1.0,
            "violation_atol": 0.0,
            "violation_rtol": 0.05,
            "transforms": [
                {
                    "op": "noise",
                    "field": "numerical_t_features",
                    "feature": "Territory.Humidity",
                    "stddev": 0.02,
                    "clamp_min": 0.0,
                    "clamp_max": 1.0,
                }
            ],
        },
    ]
