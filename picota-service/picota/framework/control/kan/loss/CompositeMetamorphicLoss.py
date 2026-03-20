from __future__ import annotations

from copy import deepcopy
from typing import Iterable

import torch
from torch import nn

from picota.framework.control.kan.loss.MetamorphicTest import MetamorphicTest
from picota.framework.control.kan.loss.MetamorphicTransform import Batch, TargetTransform
from picota.framework.control.kan.loss.TransformSet import TransformSet


class CompositeMetamorphicLoss(nn.Module):
    def __init__(
            self,
            supervised_loss: nn.Module | None = None,
            metamorphic_tests: list[MetamorphicTest] | None = None,
            transform_set: TransformSet | Iterable[object] | None = None,
            rule_specs: Iterable[object] | None = None,
            supervised_weight: float = 1.0,
            relation_constraint_weight: float = 0.0,
            worst_case_over_T_weight: float = 0.0,
            target_mapped_weight: float = 0.0,
            relation_aggregation: str = "mean",
            target_mapped_aggregation: str = "mean",
    ):
        super().__init__()
        if min(supervised_weight, relation_constraint_weight, worst_case_over_T_weight, target_mapped_weight) < 0:
            raise ValueError("weights must be >= 0")
        if relation_aggregation not in ("mean", "max"):
            raise ValueError("relation_aggregation must be 'mean' or 'max'")
        if target_mapped_aggregation not in ("mean", "max"):
            raise ValueError("target_mapped_aggregation must be 'mean' or 'max'")
        if target_mapped_weight != 0:
            raise ValueError(
                "target_mapped_weight is no longer supported for relation constraints. "
                "Encode target-mapped rules as over_T transforms and use worst_case_over_T_weight."
            )

        if rule_specs is not None and (metamorphic_tests is not None or transform_set is not None):
            raise ValueError("Use either rule_specs or (metamorphic_tests/transform_set), not both")

        self.supervised_loss = supervised_loss or nn.MSELoss()
        if rule_specs is not None:
            assigned_relation_tests, assigned_transform_set, assignment_summary = self.partitionRuleSpecsExclusive(
                rule_specs
            )
            self.relation_constraints = assigned_relation_tests
            self.over_T_transform_set = assigned_transform_set
            self.rule_assignment_summary = assignment_summary
        else:
            self.relation_constraints = list(metamorphic_tests or [])
            self.over_T_transform_set = (
                transform_set if isinstance(transform_set, TransformSet) else TransformSet(transform_set)
            )
            self.rule_assignment_summary = {
                "num_rule_specs": 0,
                "assigned_relation_constraints": len(self.relation_constraints),
                "assigned_over_T_transforms": len(self.over_T_transform_set),
                "dropped_rule_specs": 0,
                "fallback_to_relation": 0,
                "fallback_to_over_T": 0,
                "unknown_category_defaults": 0,
                "by_category": {},
            }

        self.assigned_relation_constraints = self.relation_constraints
        self.assigned_over_T_transform_set = self.over_T_transform_set
        self.supervised_weight = supervised_weight
        self.relation_constraint_weight = relation_constraint_weight
        self.worst_case_over_T_weight = worst_case_over_T_weight
        self.target_mapped_weight = 0.0
        self.relation_aggregation = relation_aggregation
        self.target_mapped_aggregation = target_mapped_aggregation
        self.last_metrics: dict[str, float | str | None] | None = None

    @staticmethod
    def _normalizeRuleCategory(category) -> str | None:
        if category is None:
            return None
        value = getattr(category, "value", category)
        if value is None:
            return None
        return str(value).strip().lower()

    @staticmethod
    def partitionRuleSpecsExclusive(rule_specs: Iterable[object] | None) -> tuple[
        list[MetamorphicTest], TransformSet, dict]:
        relation_tests: list[MetamorphicTest] = []
        over_T_transforms: list[object] = []
        summary = {
            "num_rule_specs": 0,
            "assigned_relation_constraints": 0,
            "assigned_over_T_transforms": 0,
            "dropped_rule_specs": 0,
            "fallback_to_relation": 0,
            "fallback_to_over_T": 0,
            "unknown_category_defaults": 0,
            "by_category": {},
        }
        for spec in list(rule_specs or ()):
            summary["num_rule_specs"] += 1
            category_name = CompositeMetamorphicLoss._normalizeRuleCategory(
                getattr(spec, "category", None)) or "unknown"
            summary["by_category"][category_name] = summary["by_category"].get(category_name, 0) + 1

            relation_test = getattr(spec, "relation_test", None)
            over_T_transform = getattr(spec, "over_T_transform", None)
            has_relation = relation_test is not None
            has_over_t = over_T_transform is not None
            if has_relation == has_over_t:
                summary["dropped_rule_specs"] += 1
                continue

            if has_relation:
                relation_tests.append(relation_test)
                summary["assigned_relation_constraints"] += 1
            else:
                over_T_transforms.append(over_T_transform)
                summary["assigned_over_T_transforms"] += 1

        return relation_tests, TransformSet(over_T_transforms), summary

    @classmethod
    def from_rule_specs(cls, rule_specs: Iterable[object], **kwargs) -> "CompositeMetamorphicLoss":
        return cls(rule_specs=rule_specs, **kwargs)

    def forward(self, prediction: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        return self.supervised_loss(prediction, target)

    def compute_training_loss(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
            prediction: torch.Tensor | None = None,
    ) -> torch.Tensor:
        if prediction is None:
            prediction = model(batch).squeeze()

        supervised = self.supervised_loss(prediction, target)
        relation_constraint_penalty, relation_worst_name = self._computeRelationConstraintPenalty(
            model=model,
            batch=batch,
            target=target,
            prediction=prediction,
        )
        worst_case_over_T_loss, worst_over_T_name = self._computeWorstCaseOverTLoss(
            model=model,
            batch=batch,
            target=target,
        )
        supervised_contrib = self.supervised_weight * supervised
        relation_constraint_contrib = self.relation_constraint_weight * relation_constraint_penalty
        worst_case_over_T_contrib = self.worst_case_over_T_weight * worst_case_over_T_loss
        total = supervised_contrib + relation_constraint_contrib + worst_case_over_T_contrib

        enabled_relation_constraints = bool(self.relation_constraints and (self.relation_constraint_weight > 0))
        enabled_over_t = bool(self.over_T_transform_set and (self.worst_case_over_T_weight > 0))
        if enabled_relation_constraints and enabled_over_t:
            loss_type = "composite"
        elif enabled_over_t:
            loss_type = "worst_case_over_T"
        elif enabled_relation_constraints:
            loss_type = "relation_constraints"
        else:
            loss_type = "supervised"

        self.last_metrics = {
            "loss_type": loss_type,
            "total_loss": float(total.detach().item()),
            "supervised_loss": float(supervised_contrib.detach().item()),
            "relation_constraint_penalty": float(relation_constraint_contrib.detach().item()),
            "worst_case_over_T_loss": float(worst_case_over_T_contrib.detach().item()),
            "target_mapped_supervised_loss": 0.0,
            "raw_supervised_loss": float(supervised.detach().item()),
            "raw_relation_constraint_penalty": float(relation_constraint_penalty.detach().item()),
            "raw_worst_case_over_T_loss": float(worst_case_over_T_loss.detach().item()),
            "raw_target_mapped_supervised_loss": 0.0,
            "worst_transform_name": worst_over_T_name or relation_worst_name,
            "worst_relation_constraint_name": relation_worst_name,
            "worst_over_T_transform_name": worst_over_T_name,
            "num_relation_constraints": float(len(self.relation_constraints)),
            "num_over_T_transforms": float(len(self.over_T_transform_set)),
            "num_target_mapped_terms": 0.0,
            "num_rule_specs": float(self.rule_assignment_summary.get("num_rule_specs", 0)),
            "dropped_rule_specs": float(self.rule_assignment_summary.get("dropped_rule_specs", 0)),
        }
        return total

    def _computeRelationConstraintPenalty(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
            prediction: torch.Tensor,
    ) -> tuple[torch.Tensor, str | None]:
        _ = target
        if not self.relation_constraints or self.relation_constraint_weight == 0:
            return prediction.new_tensor(0.0), None

        penalties = []
        names: list[str | None] = []
        for test in self.relation_constraints:
            transformed_batch = test.transform(self._cloneBatch(batch))
            transformed_prediction = model(transformed_batch).squeeze()
            penalties.append(test.relation.weight * test.relation.penalty(prediction, transformed_prediction))
            names.append(test.name)

        penalties_tensor = torch.stack(penalties) if penalties else prediction.new_zeros(0)
        if not penalties:
            return prediction.new_tensor(0.0), None
        if self.relation_aggregation == "max":
            relation_constraint_penalty, worst_idx_tensor = torch.max(penalties_tensor, dim=0)
            worst_idx = int(worst_idx_tensor.item()) if worst_idx_tensor.dim() == 0 else int(
                worst_idx_tensor.reshape(-1)[0]
            )
        else:
            relation_constraint_penalty = torch.mean(penalties_tensor)
            worst_idx = int(torch.argmax(penalties_tensor).item())
        return relation_constraint_penalty, names[worst_idx]

    def _computeWorstCaseOverTLoss(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
    ) -> tuple[torch.Tensor, str | None]:
        if not self.over_T_transform_set or self.worst_case_over_T_weight == 0:
            return target.new_tensor(0.0), None

        transformed_losses = []
        transform_names: list[str | None] = []
        for transform_spec in self.over_T_transform_set:
            transformed_batch = transform_spec.transform(self._cloneBatch(batch))
            transformed_prediction = model(transformed_batch).squeeze()
            transformed_target = self._applyTargetTransform(
                transform_spec.target_transform,
                target=target,
                source_batch=batch,
                transformed_batch=transformed_batch,
            )
            transformed_loss = self.supervised_loss(transformed_prediction, transformed_target)
            transform_weight = float(getattr(transform_spec, "weight", 1.0))
            transformed_losses.append(transform_weight * transformed_loss)
            transform_names.append(transform_spec.name)

        if not transformed_losses:
            return target.new_tensor(0.0), None
        transformed_losses_tensor = torch.stack(transformed_losses)
        worst_loss, worst_idx_tensor = torch.max(transformed_losses_tensor, dim=0)
        worst_idx = int(worst_idx_tensor.item()) if worst_idx_tensor.dim() == 0 else int(
            worst_idx_tensor.reshape(-1)[0])
        return worst_loss, transform_names[worst_idx]

    @staticmethod
    def _cloneBatch(batch: Batch) -> Batch:
        cloned: Batch = {}
        for key, value in batch.items():
            if torch.is_tensor(value):
                cloned[key] = value.clone()
            else:
                cloned[key] = deepcopy(value)
        return cloned

    @staticmethod
    def _cloneTarget(target: torch.Tensor) -> torch.Tensor:
        return target.clone() if torch.is_tensor(target) else deepcopy(target)

    @staticmethod
    def _applyTargetTransform(
            target_transform: TargetTransform | None,
            target: torch.Tensor,
            source_batch: Batch,
            transformed_batch: Batch,
    ) -> torch.Tensor:
        if target_transform is None:
            return target
        cloned_target = CompositeMetamorphicLoss._cloneTarget(target)
        try:
            return target_transform(cloned_target, source_batch, transformed_batch)
        except TypeError:
            return target_transform(cloned_target)


__all__ = ["CompositeMetamorphicLoss"]
