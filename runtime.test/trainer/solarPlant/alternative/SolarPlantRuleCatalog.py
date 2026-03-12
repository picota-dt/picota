from __future__ import annotations

from typing import Iterable, Mapping

from kan.MetamorphicCatalog import (
    CatalogRuleSpec,
    RuleCategory,
    add_to_numerical_t_feature,
    make_greater_or_equal_test,
    make_monotonic_test,
)

# Editable per-rule weights for this SolarPlant example.
DEFAULT_SOLAR_PLANT_RULE_WEIGHT_MAP: dict[str, float] = {
    "radiation_up_implies_active_power_non_decreasing": 1.0,
    "cell_temperature_up_tends_to_reduce_efficiency": 0.3,
}


def build_solar_plant_active_power_rule_specs(
        numerical_t_feature_names: Iterable[str],
        rule_weight_map: Mapping[str, float] | None = None,
) -> tuple[list[CatalogRuleSpec], dict[str, float], list[str]]:
    """
    Build example-specific rule specs for SolarPlant active power.

    This module is intentionally example-level (SolarPlant/Infecar names), while
    the trainer remains framework-generic.
    """
    names = list(numerical_t_feature_names)
    weights = dict(DEFAULT_SOLAR_PLANT_RULE_WEIGHT_MAP)
    if rule_weight_map is not None:
        for key, value in rule_weight_map.items():
            weights[key] = float(value)
    for key, value in weights.items():
        if value < 0:
            raise ValueError(f"Rule weight for '{key}' must be >= 0")

    specs: list[CatalogRuleSpec] = []
    active_rule_names: list[str] = []
    effective_weights: dict[str, float] = {}

    rule_name = "radiation_up_implies_active_power_non_decreasing"
    if "Infecar.radiation" in names:
        idx = names.index("Infecar.radiation")
        weight = float(weights.get(rule_name, 1.0))
        active_rule_names.append(rule_name)
        effective_weights[rule_name] = weight
        specs.append(
            CatalogRuleSpec(
                name=rule_name,
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_greater_or_equal_test(
                    transform=add_to_numerical_t_feature(index=idx, delta=25.0),
                    name=rule_name,
                    weight=weight,
                ),
                description="Increasing radiation should not decrease predicted active power",
                consistency_profile="current_numerical_only",
            )
        )

    rule_name = "cell_temperature_up_tends_to_reduce_efficiency"
    if "cellTemperature" in names:
        idx = names.index("cellTemperature")
        weight = float(weights.get(rule_name, 1.0))
        active_rule_names.append(rule_name)
        effective_weights[rule_name] = weight
        specs.append(
            CatalogRuleSpec(
                name=rule_name,
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_monotonic_test(
                    transform=add_to_numerical_t_feature(index=idx, delta=1.0),
                    direction="decreasing",
                    name=rule_name,
                    weight=weight,
                ),
                description="Increasing cell temperature should not increase active power",
                consistency_profile="current_numerical_only",
            )
        )

    inactive = sorted(set(weights.keys()) - set(active_rule_names))
    return specs, effective_weights, inactive
