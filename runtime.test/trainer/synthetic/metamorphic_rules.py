from __future__ import annotations

from dataclasses import dataclass

import torch

from kan.MetamorphicCatalog import (
    CatalogRuleSpec,
    RuleCategory,
    make_equal_test,
    make_greater_or_equal_test,
    make_transform,
    scale_target,
)
from kan.MetamorphicLoss import Batch, BatchTransform


@dataclass(frozen=True)
class AuditFeatureIndices:
    area: int
    rain: int
    humidity: int
    spur: int


def resolve_audit_feature_indices(numerical_t_feature_names: list[str]) -> AuditFeatureIndices:
    missing = [name for name in ("area", "rain", "humidity", "spur") if name not in numerical_t_feature_names]
    if missing:
        raise ValueError(
            "Synthetic audit dataset is missing required numerical features: "
            f"{', '.join(missing)}. Available: {numerical_t_feature_names}"
        )
    return AuditFeatureIndices(
        area=numerical_t_feature_names.index("area"),
        rain=numerical_t_feature_names.index("rain"),
        humidity=numerical_t_feature_names.index("humidity"),
        spur=numerical_t_feature_names.index("spur"),
    )


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


def _add_gaussian_noise_current_and_lookback(index: int, sigma: float) -> BatchTransform:
    if sigma < 0:
        raise ValueError("sigma must be >= 0")

    def _add_noise(values: torch.Tensor) -> torch.Tensor:
        noisy = values + (sigma * torch.randn_like(values))
        # Keep humidity in [0, 1] when values are normalized.
        max_abs = float(torch.max(torch.abs(values)).item()) if values.numel() > 0 else 0.0
        if max_abs <= 1.5:
            noisy = torch.clamp(noisy, min=0.0, max=1.0)
        return noisy

    def _transform(batch: Batch) -> Batch:
        numerical_t = batch["numerical_t_features"].clone()
        if numerical_t.shape[-1] > index:
            numerical_t[..., index] = _add_noise(numerical_t[..., index])
        batch["numerical_t_features"] = numerical_t

        if "numerical_lookback_features" in batch:
            numerical_lb = batch["numerical_lookback_features"].clone()
            if numerical_lb.shape[-1] > index:
                numerical_lb[..., index] = _add_noise(numerical_lb[..., index])
            batch["numerical_lookback_features"] = numerical_lb
        return batch

    return _transform


def build_synthetic_audit_rule_specs(
        numerical_t_feature_names: list[str],
        include_target_mapped_over_T_transforms: bool = True,
) -> list[CatalogRuleSpec]:
    idx = resolve_audit_feature_indices(numerical_t_feature_names)
    specs: list[CatalogRuleSpec] = []

    # y proportional to area
    # Encoded only as over-T target-mapped rules to avoid overlap with relation constraints.
    for factor, tolerance in ((1.05, 0.10), (1.10, 0.10), (1.20, 0.12)):
        suffix = f"{factor:.2f}".replace(".", "_")
        name = f"y_proportional_to_area_x{suffix}"
        transform = _scale_numerical_feature_current_and_lookback(index=idx.area, factor=factor)
        if include_target_mapped_over_T_transforms:
            specs.append(
                CatalogRuleSpec(
                    name=f"{name}_target_mapped",
                    category=RuleCategory.TARGET_MAPPED,
                    over_T_transform=make_transform(
                        transform=transform,
                        target_transform=scale_target(factor),
                        name=f"{name}_target_mapped",
                    ),
                    description=(
                        f"y proportional to area under scaling x{factor:.2f} "
                        f"(relative tolerance {tolerance:.2f})"
                    ),
                    consistency_profile=f"current_and_lookback_area_scaling_x{suffix}",
                )
            )

    # y non-decreasing when rain increases
    for factor, tolerance in ((1.02, 0.02), (1.05, 0.03), (1.10, 0.04)):
        suffix = f"{factor:.2f}".replace(".", "_")
        name = f"y_non_decreasing_when_rain_increases_x{suffix}"
        transform = _scale_numerical_feature_current_and_lookback(index=idx.rain, factor=factor)
        specs.append(
            CatalogRuleSpec(
                name=name,
                category=RuleCategory.DIRECTIONAL_ORDINAL,
                relation_test=make_greater_or_equal_test(
                    transform=transform,
                    name=name,
                    weight=1.0,
                    violation_rtol=tolerance,
                ),
                description=f"y should not decrease when rain is scaled by {factor:.2f}",
                consistency_profile=f"current_and_lookback_rain_scaling_x{suffix}",
            )
        )

    # y robust to humidity noise
    for sigma, tolerance in ((0.01, 0.03), (0.02, 0.04), (0.05, 0.06)):
        sigma_label = f"{sigma:.2f}".replace(".", "_")
        name = f"y_robust_to_humidity_noise_sigma_{sigma_label}"
        transform = _add_gaussian_noise_current_and_lookback(index=idx.humidity, sigma=sigma)
        specs.append(
            CatalogRuleSpec(
                name=name,
                category=RuleCategory.INVARIANCE,
                relation_test=make_equal_test(
                    transform=transform,
                    name=name,
                    weight=1.0,
                    violation_rtol=tolerance,
                ),
                description=f"y should be invariant under humidity Gaussian noise sigma={sigma:.2f}",
                consistency_profile=f"current_and_lookback_humidity_noise_sigma_{sigma_label}",
            )
        )

    return specs
