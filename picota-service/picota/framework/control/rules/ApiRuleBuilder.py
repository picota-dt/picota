from __future__ import annotations

from typing import Any, Callable

import torch

from picota.framework.control.kan.MetamorphicCatalog import (
    CatalogRuleSpec,
    RuleCategory,
    identity,
    make_equal_test,
    make_greater_or_equal_test,
    make_greater_test,
    make_lower_or_equal_test,
    make_lower_test,
    make_monotonic_test,
    make_proportional_test,
    make_transform,
    scale_target,
    shift_target,
)
from picota.framework.control.rules.apiRuleBuilder.RuleBuildContext import RuleBuildContext

Batch = dict[str, torch.Tensor]
BatchTransform = Callable[[Batch], Batch]


class ApiRuleBuilder:
    VALID_FIELDS = {
        "t",
        "numerical_t_features",
        "categorical_t_features",
        "lookback_t",
        "numerical_lookback_features",
        "categorical_lookback_features",
    }

    @staticmethod
    def buildRuleSpecsFromApi(
            raw_rule_specs: list[dict[str, Any]] | None,
            *,
            feature_names: dict[str, list[str]] | None = None,
    ) -> list[CatalogRuleSpec]:
        if raw_rule_specs is None:
            return []
        context = RuleBuildContext(feature_names=feature_names or {})
        built: list[CatalogRuleSpec] = []
        for index, raw_spec in enumerate(raw_rule_specs):
            if not isinstance(raw_spec, dict):
                raise ValueError(f"metamorphic.rule_specs[{index}] must be an object")
            built.append(ApiRuleBuilder._buildSingleRuleSpec(raw_spec, index=index, context=context))
        return built

    @staticmethod
    def _buildSingleRuleSpec(
            raw_spec: dict[str, Any],
            *,
            index: int,
            context: RuleBuildContext,
    ) -> CatalogRuleSpec:
        name = str(raw_spec.get("name") or f"rule_{index}")
        category = ApiRuleBuilder._parseCategory(raw_spec.get("category"))
        spec_kind = str(raw_spec.get("kind") or "relation").strip().lower()
        description = raw_spec.get("description")
        consistency_profile = raw_spec.get("consistency_profile")
        transform = ApiRuleBuilder._buildTransform(
            raw_spec.get("transforms"),
            context=context,
            key_prefix=f"rule_specs[{index}]",
        )

        if spec_kind == "relation":
            relation_kind = str(raw_spec.get("relation") or "equal").strip().lower()
            weight = float(raw_spec.get("weight", 1.0))
            margin = float(raw_spec.get("margin", 0.0))
            direction = str(raw_spec.get("direction", "increasing")).strip().lower()
            factor = float(raw_spec.get("factor", 1.0))
            violation_atol = ApiRuleBuilder._readOptionalFloat(raw_spec.get("violation_atol"))
            violation_rtol = ApiRuleBuilder._readOptionalFloat(raw_spec.get("violation_rtol"))

            if relation_kind == "equal":
                relation_test = make_equal_test(
                    transform=transform,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "greater":
                relation_test = make_greater_test(
                    transform=transform,
                    margin=margin,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "greater_or_equal":
                relation_test = make_greater_or_equal_test(
                    transform=transform,
                    margin=margin,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "lower":
                relation_test = make_lower_test(
                    transform=transform,
                    margin=margin,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "lower_or_equal":
                relation_test = make_lower_or_equal_test(
                    transform=transform,
                    margin=margin,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "monotonic":
                relation_test = make_monotonic_test(
                    transform=transform,
                    direction=direction,
                    margin=margin,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            elif relation_kind == "proportional":
                relation_test = make_proportional_test(
                    transform=transform,
                    factor=factor,
                    name=name,
                    weight=weight,
                    violation_atol=violation_atol,
                    violation_rtol=violation_rtol,
                )
            else:
                raise ValueError(f"Unsupported relation kind '{relation_kind}' for rule '{name}'")

            return CatalogRuleSpec(
                name=name,
                category=category,
                relation_test=relation_test,
                description=str(description) if description is not None else None,
                consistency_profile=str(consistency_profile) if consistency_profile is not None else None,
            )

        if spec_kind == "over_t":
            weight = float(raw_spec.get("weight", 1.0))
            target_transform = ApiRuleBuilder._buildTargetTransform(raw_spec.get("target_transform"))
            over_t_transform = make_transform(
                transform=transform,
                name=name,
                weight=weight,
                target_transform=target_transform,
            )
            return CatalogRuleSpec(
                name=name,
                category=category,
                over_T_transform=over_t_transform,
                description=str(description) if description is not None else None,
                consistency_profile=str(consistency_profile) if consistency_profile is not None else None,
            )

        raise ValueError(f"Unsupported rule spec kind '{spec_kind}' for rule '{name}'")

    @staticmethod
    def _buildTransform(
            raw_ops: Any,
            *,
            context: RuleBuildContext,
            key_prefix: str,
    ) -> BatchTransform:
        if raw_ops is None:
            return identity()
        if isinstance(raw_ops, dict):
            raw_ops = [raw_ops]
        if not isinstance(raw_ops, list):
            raise ValueError(f"{key_prefix}.transforms must be a list or object")

        ops = [op for op in raw_ops if op is not None]
        if not ops:
            return identity()

        transforms = [
            ApiRuleBuilder._buildOperation(
                op,
                context=context,
                key_prefix=f"{key_prefix}.transforms[{idx}]",
            )
            for idx, op in enumerate(ops)
        ]

        def _transform(batch: Batch) -> Batch:
            current = batch
            for transform in transforms:
                current = transform(current)
            return current

        return _transform

    @staticmethod
    def _buildOperation(raw_op: Any, *, context: RuleBuildContext, key_prefix: str) -> BatchTransform:
        if not isinstance(raw_op, dict):
            raise ValueError(f"{key_prefix} must be an object")
        op = str(raw_op.get("op") or raw_op.get("type") or "").strip().lower()
        field = str(raw_op.get("field") or "").strip()
        if not field:
            raise ValueError(f"{key_prefix}.field is required")
        if field not in ApiRuleBuilder.VALID_FIELDS:
            raise ValueError(f"{key_prefix}.field '{field}' is not supported")

        if op == "zero":
            def _zero(batch: Batch) -> Batch:
                tensor = batch[field].clone()
                batch[field] = torch.zeros_like(tensor)
                return batch

            return _zero

        feature_index = context.resolve_index(field, raw_op, key_prefix=key_prefix)

        if op == "add":
            delta = float(raw_op.get("delta", 0.0))

            def _add(batch: Batch) -> Batch:
                tensor = batch[field].clone()
                tensor[..., feature_index] = tensor[..., feature_index] + delta
                batch[field] = tensor
                return batch

            return _add

        if op == "scale":
            factor = float(raw_op.get("factor", 1.0))

            def _scale(batch: Batch) -> Batch:
                tensor = batch[field].clone()
                tensor[..., feature_index] = tensor[..., feature_index] * factor
                batch[field] = tensor
                return batch

            return _scale

        if op == "scale_if_positive":
            factor = float(raw_op.get("factor", 1.0))

            def _scale_if_positive(batch: Batch) -> Batch:
                tensor = batch[field].clone()
                feature = tensor[..., feature_index]
                tensor[..., feature_index] = torch.where(feature > 0.0, feature * factor, feature)
                batch[field] = tensor
                return batch

            return _scale_if_positive

        if op in {"noise", "gaussian_noise"}:
            stddev = float(raw_op.get("stddev", 0.01))
            clamp_min = raw_op.get("clamp_min")
            clamp_max = raw_op.get("clamp_max")
            clamp_min = None if clamp_min is None else float(clamp_min)
            clamp_max = None if clamp_max is None else float(clamp_max)

            def _noise(batch: Batch) -> Batch:
                tensor = batch[field].clone()
                feature = tensor[..., feature_index]
                feature = feature + torch.randn_like(feature) * stddev
                if clamp_min is not None or clamp_max is not None:
                    feature = torch.clamp(feature, min=clamp_min, max=clamp_max)
                tensor[..., feature_index] = feature
                batch[field] = tensor
                return batch

            return _noise

        raise ValueError(f"{key_prefix}.op '{op}' is not supported")

    @staticmethod
    def _buildTargetTransform(raw_target_transform: Any):
        if raw_target_transform is None:
            return None
        if not isinstance(raw_target_transform, dict):
            raise ValueError("target_transform must be an object")
        kind = str(raw_target_transform.get("kind") or raw_target_transform.get("type") or "").strip().lower()
        if kind == "scale":
            return scale_target(float(raw_target_transform.get("factor", 1.0)))
        if kind == "shift":
            return shift_target(float(raw_target_transform.get("delta", 0.0)))
        raise ValueError(f"Unsupported target_transform kind '{kind}'")

    @staticmethod
    def _parseCategory(raw_category: Any) -> RuleCategory:
        category = str(raw_category or RuleCategory.DIRECTIONAL_ORDINAL.value).strip().lower()
        if category == RuleCategory.INVARIANCE.value:
            return RuleCategory.INVARIANCE
        if category == RuleCategory.DIRECTIONAL_ORDINAL.value:
            return RuleCategory.DIRECTIONAL_ORDINAL
        if category == RuleCategory.TARGET_MAPPED.value:
            return RuleCategory.TARGET_MAPPED
        raise ValueError(f"Unsupported rule category '{category}'")

    @staticmethod
    def _readOptionalFloat(value: Any) -> float | None:
        if value is None:
            return None
        return float(value)
