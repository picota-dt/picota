from __future__ import annotations

from typing import Iterable

import torch

from kan.MetamorphicCatalog import (
    CatalogRuleSpec,
    RuleCategory,
    compose,
    make_equal_test,
    make_greater_or_equal_test,
    make_proportional_test,
    make_transform,
    scale_numerical_t_feature,
    scale_target,
)
from kan.MetamorphicLoss import Batch, BatchTransform


def scale_numerical_t_features(indices: Iterable[int], factor: float) -> BatchTransform:
    feature_indices = tuple(sorted({int(index) for index in indices}))

    def _transform(batch: Batch) -> Batch:
        tensor = batch["numerical_t_features"].clone()
        for feature_index in feature_indices:
            tensor[..., feature_index] = tensor[..., feature_index] * factor
        batch["numerical_t_features"] = tensor
        return batch

    return _transform


def redistribute_numerical_t_features(index_from: int, index_to: int, ratio: float) -> BatchTransform:
    if ratio < 0:
        raise ValueError("ratio must be >= 0")

    def _transform(batch: Batch) -> Batch:
        tensor = batch["numerical_t_features"].clone()
        source = tensor[..., index_from]
        target = tensor[..., index_to]
        total = source + target
        transfer = torch.minimum(source, total * ratio)
        tensor[..., index_from] = source - transfer
        tensor[..., index_to] = target + transfer
        batch["numerical_t_features"] = tensor
        return batch

    return _transform


def build_spanish_homes_monetary_spending_rule_specs(
        numerical_t_feature_names: Iterable[str],
        include_target_mapped: bool = True,
) -> list[CatalogRuleSpec]:
    names = list(numerical_t_feature_names)
    specs: list[CatalogRuleSpec] = []

    if "ingresosNetos" in names:
        income_index = names.index("ingresosNetos")
        income_up = scale_numerical_t_feature(income_index, factor=1.05)
        specs.append(
            CatalogRuleSpec(
                name="net_income_up_implies_monetary_spending_non_decreasing",
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_greater_or_equal_test(
                    income_up,
                    name="net_income_up_implies_monetary_spending_non_decreasing",
                    weight=1.0,
                    violation_rtol=0.02,
                ),
                description="Monetary spending should not decrease when net income increases",
                consistency_profile="current_step_numerical_scaling",
            )
        )
    else:
        income_index = None

    member_indices = [index for index, name in enumerate(names) if name.startswith("miembros:")]
    if income_index is not None and member_indices:
        members_and_income_up = compose(
            scale_numerical_t_features(member_indices, factor=1.10),
            scale_numerical_t_feature(income_index, factor=1.10),
        )
        specs.append(
            CatalogRuleSpec(
                name="basic_needs_spending_scales_with_household_size_under_constant_income_per_capita",
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_proportional_test(
                    members_and_income_up,
                    factor=1.10,
                    name="basic_needs_spending_scales_with_household_size_under_constant_income_per_capita",
                    weight=1.0,
                    violation_rtol=0.10,
                ),
                description="Scaling members and net income together should scale monetary spending proportionally",
                consistency_profile="current_step_coupled_members_income_scaling",
            )
        )

        if include_target_mapped:
            specs.append(
                CatalogRuleSpec(
                    name="members_income_scaling_target_mapped_proxy",
                    category=RuleCategory.TARGET_MAPPED,
                    over_T_transform=make_transform(
                        transform=members_and_income_up,
                        target_transform=scale_target(1.10),
                        name="members_income_scaling_target_mapped_proxy",
                    ),
                    description=(
                        "Optional target-mapped proxy: scaling members and income by 1.10 scales spending target by 1.10"
                    ),
                    consistency_profile="current_step_coupled_members_income_scaling",
                )
            )

    if "ipc" in names:
        cpi_index = names.index("ipc")
        cpi_up = scale_numerical_t_feature(cpi_index, factor=1.02)
        specs.append(
            CatalogRuleSpec(
                name="cpi_up_implies_nominal_spending_non_decreasing",
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_greater_or_equal_test(
                    cpi_up,
                    name="cpi_up_implies_nominal_spending_non_decreasing",
                    weight=0.7,
                    violation_rtol=0.02,
                ),
                description="Nominal spending should not decrease when CPI increases",
                consistency_profile="current_step_numerical_scaling",
            )
        )

    male_income_name = "miembros:conIngresos:masculinos"
    female_income_name = "miembros:conIngresos:femeninos"
    if male_income_name in names and female_income_name in names:
        male_income_index = names.index(male_income_name)
        female_income_index = names.index(female_income_name)
        gender_redistribution = redistribute_numerical_t_features(
            index_from=male_income_index,
            index_to=female_income_index,
            ratio=0.02,
        )
        specs.append(
            CatalogRuleSpec(
                name="spending_invariant_under_gender_income_redistribution",
                category=RuleCategory.INVARIANCE,
                relation_test=make_equal_test(
                    gender_redistribution,
                    name="spending_invariant_under_gender_income_redistribution",
                    weight=0.5,
                    violation_rtol=0.03,
                ),
                description="Spending should be invariant to gender-income distribution shifts preserving totals",
                consistency_profile="current_step_member_income_redistribution_preserve_total",
            )
        )

    return specs
