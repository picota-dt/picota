from __future__ import annotations

from abc import ABC, abstractmethod
from copy import deepcopy
from dataclasses import dataclass
from enum import Enum
from typing import Callable, Iterable

import torch
from torch import nn

Batch = dict[str, torch.Tensor]
BatchTransform = Callable[[Batch], Batch]
# Supported signatures:
#   target_transform(target)
#   target_transform(target, source_batch, transformed_batch)
TargetTransform = Callable[..., torch.Tensor]


class MetamorphicRelationKind(str, Enum):
    EQUAL = "Equal"
    MONOTONIC = "Monotonic"
    GREATER = "Greater"
    LOWER = "Lower"
    GREATER_OR_EQUAL = "GreaterOrEqual"
    LOWER_OR_EQUAL = "LowerOrEqual"
    PROPORTIONAL = "Proportional"


class MetamorphicRelation(ABC):
    def __init__(self, weight: float = 1.0):
        if weight < 0:
            raise ValueError("weight must be >= 0")
        self.weight = weight

    @property
    @abstractmethod
    def kind(self) -> MetamorphicRelationKind:
        raise NotImplementedError

    @abstractmethod
    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        raise NotImplementedError


class Equal(MetamorphicRelation):
    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.EQUAL

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        return torch.mean(torch.abs(transformed_prediction - base_prediction))


class Greater(MetamorphicRelation):
    def __init__(self, margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.GREATER

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        # Enforce transformed > base + margin (strictness is approximated with hinge + margin)
        return torch.relu((base_prediction + self.margin) - transformed_prediction).mean()


class Lower(MetamorphicRelation):
    def __init__(self, margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.LOWER

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        # Enforce transformed < base - margin (strictness is approximated with hinge + margin)
        return torch.relu(transformed_prediction - (base_prediction - self.margin)).mean()


class GreaterOrEqual(MetamorphicRelation):
    def __init__(self, margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.GREATER_OR_EQUAL

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        return torch.relu((base_prediction + self.margin) - transformed_prediction).mean()


class LowerOrEqual(MetamorphicRelation):
    def __init__(self, margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.LOWER_OR_EQUAL

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        return torch.relu(transformed_prediction - (base_prediction - self.margin)).mean()


class Monotonic(MetamorphicRelation):
    def __init__(self, direction: str = "increasing", margin: float = 0.0, weight: float = 1.0):
        super().__init__(weight=weight)
        if direction not in ("increasing", "decreasing"):
            raise ValueError("direction must be 'increasing' or 'decreasing'")
        self.direction = direction
        self.margin = margin

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.MONOTONIC

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        if self.direction == "increasing":
            return torch.relu((base_prediction + self.margin) - transformed_prediction).mean()
        return torch.relu(transformed_prediction - (base_prediction - self.margin)).mean()


class Proportional(MetamorphicRelation):
    def __init__(self, factor: float, weight: float = 1.0, eps: float = 1e-8):
        super().__init__(weight=weight)
        self.factor = factor
        self.eps = eps

    @property
    def kind(self) -> MetamorphicRelationKind:
        return MetamorphicRelationKind.PROPORTIONAL

    def penalty(self, base_prediction: torch.Tensor, transformed_prediction: torch.Tensor) -> torch.Tensor:
        expected = base_prediction * self.factor
        scale = torch.clamp(torch.abs(expected), min=self.eps)
        return torch.mean(torch.abs(transformed_prediction - expected) / scale)


@dataclass(frozen=True)
class MetamorphicTransform:
    """
    Paper-style transformation element t in T.

    `target_transform` allows label-preserving (None) or label-mapped MRs.
    """

    transform: BatchTransform
    target_transform: TargetTransform | None = None
    name: str | None = None


class TransformSet:
    """
    Explicit container for the paper-style set T of metamorphic transformations.
    """

    def __init__(self, transforms: Iterable[MetamorphicTransform] | None = None):
        self.transforms = tuple(transforms or ())

    def __iter__(self):
        return iter(self.transforms)

    def __len__(self) -> int:
        return len(self.transforms)

    def __bool__(self) -> bool:
        return len(self.transforms) > 0


@dataclass(frozen=True)
class MetamorphicTest:
    """
    Relation-based constraint (custom extension).

    It can optionally carry `target_transform` for frameworks that support target-mapped
    training objectives, even if the relation penalty itself does not use it directly.
    """

    relation: MetamorphicRelation
    transform: BatchTransform
    name: str | None = None
    target_transform: TargetTransform | None = None


def _normalize_rule_category(category) -> str | None:
    if category is None:
        return None
    value = getattr(category, "value", category)
    if value is None:
        return None
    return str(value).strip().lower()


def partition_rule_specs_exclusive(rule_specs: Iterable[object] | None) -> tuple[
    list[MetamorphicTest], TransformSet, dict]:
    """
    Partition catalog rule specs into relation vs paper branches with an exclusive policy.

    Preferred assignment by category:
    - invariance -> paper
    - target_mapped -> paper
    - directional_ordinal -> relation

    Fallbacks are applied when the preferred artifact is not available.
    """
    relation_tests: list[MetamorphicTest] = []
    paper_transforms: list[MetamorphicTransform] = []
    summary = {
        "num_rule_specs": 0,
        "assigned_relation_tests": 0,
        "assigned_paper_transforms": 0,
        "dropped_rule_specs": 0,
        "fallback_to_relation": 0,
        "fallback_to_paper": 0,
        "unknown_category_defaults": 0,
        "by_category": {},
    }
    category_preference = {
        "invariance": "paper",
        "target_mapped": "paper",
        "directional_ordinal": "relation",
    }

    for spec in list(rule_specs or ()):
        summary["num_rule_specs"] += 1
        name = getattr(spec, "name", None)
        category_name = _normalize_rule_category(getattr(spec, "category", None)) or "unknown"
        summary["by_category"][category_name] = summary["by_category"].get(category_name, 0) + 1

        relation_test = getattr(spec, "relation_test", None)
        paper_transform = getattr(spec, "paper_transform", None)
        if relation_test is None and paper_transform is None:
            summary["dropped_rule_specs"] += 1
            continue

        preferred = category_preference.get(category_name)
        if preferred is None:
            # Deterministic default for unknown categories to avoid overlaps.
            if paper_transform is not None and relation_test is not None:
                preferred = "paper"
                summary["unknown_category_defaults"] += 1
            elif paper_transform is not None:
                preferred = "paper"
            else:
                preferred = "relation"

        if preferred == "paper":
            if paper_transform is not None:
                paper_transforms.append(paper_transform)
                summary["assigned_paper_transforms"] += 1
            elif relation_test is not None:
                relation_tests.append(relation_test)
                summary["assigned_relation_tests"] += 1
                summary["fallback_to_relation"] += 1
        else:
            if relation_test is not None:
                relation_tests.append(relation_test)
                summary["assigned_relation_tests"] += 1
            elif paper_transform is not None:
                paper_transforms.append(paper_transform)
                summary["assigned_paper_transforms"] += 1
                summary["fallback_to_paper"] += 1

        # Keep linter quiet for debugging expansions where `name` is useful.
        _ = name

    return relation_tests, TransformSet(paper_transforms), summary


class CompositeMetamorphicLoss(nn.Module):
    """
    Unified metamorphic loss.

    It can combine:
    - supervised source loss               : l(f(x), y)
    - paper-style worst-case transformed   : max_t l(f(t(x)), y_t)
    - relation penalties                   : Agg_i penalty_i(f(x), f(t_i(x)))
    - target-mapped supervised (relations) : Agg_j l(f(t_j(x)), g_j(y))

    Use weights to enable/disable each term.
    """

    def __init__(
            self,
            supervised_loss: nn.Module | None = None,
            metamorphic_tests: list[MetamorphicTest] | None = None,
            transform_set: TransformSet | Iterable[MetamorphicTransform] | None = None,
            rule_specs: Iterable[object] | None = None,
            supervised_weight: float = 1.0,
            relation_weight: float = 0.0,
            paper_worst_case_weight: float = 0.0,
            target_mapped_weight: float = 0.0,
            relation_aggregation: str = "mean",
            target_mapped_aggregation: str = "mean",
    ):
        super().__init__()
        if min(supervised_weight, relation_weight, paper_worst_case_weight, target_mapped_weight) < 0:
            raise ValueError("weights must be >= 0")
        if relation_aggregation not in ("mean", "max"):
            raise ValueError("relation_aggregation must be 'mean' or 'max'")
        if target_mapped_aggregation not in ("mean", "max"):
            raise ValueError("target_mapped_aggregation must be 'mean' or 'max'")

        if rule_specs is not None and (metamorphic_tests is not None or transform_set is not None):
            raise ValueError("Use either rule_specs or (metamorphic_tests/transform_set), not both")

        self.supervised_loss = supervised_loss or nn.MSELoss()
        if rule_specs is not None:
            assigned_relation_tests, assigned_transform_set, assignment_summary = partition_rule_specs_exclusive(
                rule_specs)
            self.metamorphic_tests = assigned_relation_tests
            self.transform_set = assigned_transform_set
            self.rule_assignment_summary = assignment_summary
        else:
            self.metamorphic_tests = list(metamorphic_tests or [])
            self.transform_set = transform_set if isinstance(transform_set, TransformSet) else TransformSet(
                transform_set)
            self.rule_assignment_summary = {
                "num_rule_specs": 0,
                "assigned_relation_tests": len(self.metamorphic_tests),
                "assigned_paper_transforms": len(self.transform_set),
                "dropped_rule_specs": 0,
                "fallback_to_relation": 0,
                "fallback_to_paper": 0,
                "unknown_category_defaults": 0,
                "by_category": {},
            }

        # Explicit aliases to make external use (evaluation/reporting) transparent.
        self.assigned_relation_tests = self.metamorphic_tests
        self.assigned_transform_set = self.transform_set
        self.supervised_weight = supervised_weight
        self.relation_weight = relation_weight
        self.paper_worst_case_weight = paper_worst_case_weight
        self.target_mapped_weight = target_mapped_weight
        self.relation_aggregation = relation_aggregation
        self.target_mapped_aggregation = target_mapped_aggregation
        self.last_metrics: dict[str, float | str | None] | None = None

    @classmethod
    def from_rule_specs(
            cls,
            rule_specs: Iterable[object],
            **kwargs,
    ) -> "CompositeMetamorphicLoss":
        return cls(rule_specs=rule_specs, **kwargs)

    def forward(self, prediction: torch.Tensor, target: torch.Tensor) -> torch.Tensor:
        # Backward-compatible behavior when used as a plain loss(pred, target).
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

        relation_penalty, relation_worst_name, relation_raw = self._compute_relation_penalty(
            model=model,
            batch=batch,
            target=target,
            prediction=prediction,
        )
        paper_worst_case_loss, paper_worst_name = self._compute_paper_worst_case_loss(
            model=model,
            batch=batch,
            target=target,
        )
        target_mapped_supervised = self._compute_relation_target_mapped_supervised(
            model=model,
            batch=batch,
            target=target,
        )

        supervised_contrib = self.supervised_weight * supervised
        relation_contrib = self.relation_weight * relation_penalty
        paper_contrib = self.paper_worst_case_weight * paper_worst_case_loss
        target_mapped_contrib = self.target_mapped_weight * target_mapped_supervised
        total = supervised_contrib + relation_contrib + paper_contrib + target_mapped_contrib

        enabled_relation = bool(self.metamorphic_tests and (self.relation_weight > 0))
        enabled_paper = bool(self.transform_set and (self.paper_worst_case_weight > 0))
        enabled_target_mapped = bool(
            self.metamorphic_tests and (self.target_mapped_weight > 0) and relation_raw["num_target_mapped_terms"] > 0
        )
        if enabled_relation and enabled_paper:
            loss_type = "composite"
        elif enabled_paper:
            loss_type = "paper"
        elif enabled_relation or enabled_target_mapped:
            loss_type = "relation"
        else:
            loss_type = "supervised"

        weighted_meta = relation_contrib + paper_contrib
        raw_meta = relation_penalty + paper_worst_case_loss
        self.last_metrics = {
            "loss_type": loss_type,
            "total_loss": float(total.detach().item()),
            "supervised_loss": float(supervised_contrib.detach().item()),
            "metamorphic_penalty": float(weighted_meta.detach().item()),
            "target_mapped_supervised_loss": float(target_mapped_contrib.detach().item()),
            "raw_supervised_loss": float(supervised.detach().item()),
            "raw_metamorphic_penalty": float(raw_meta.detach().item()),
            "raw_relation_penalty": float(relation_penalty.detach().item()),
            "raw_paper_worst_case_loss": float(paper_worst_case_loss.detach().item()),
            "raw_target_mapped_supervised_loss": float(target_mapped_supervised.detach().item()),
            "worst_transform_name": paper_worst_name or relation_worst_name,
            "worst_relation_test_name": relation_worst_name,
            "worst_paper_transform_name": paper_worst_name,
            "num_relation_tests": float(len(self.metamorphic_tests)),
            "num_paper_transforms": float(len(self.transform_set)),
            "num_target_mapped_terms": float(relation_raw["num_target_mapped_terms"]),
            "num_rule_specs": float(self.rule_assignment_summary.get("num_rule_specs", 0)),
            "dropped_rule_specs": float(self.rule_assignment_summary.get("dropped_rule_specs", 0)),
        }
        return total

    def _compute_relation_penalty(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
            prediction: torch.Tensor,
    ) -> tuple[torch.Tensor, str | None, dict[str, int]]:
        _ = target  # kept for symmetry/future extensions
        if not self.metamorphic_tests:
            zero = prediction.new_tensor(0.0)
            return zero, None, {"num_target_mapped_terms": 0}
        if self.relation_weight == 0:
            zero = prediction.new_tensor(0.0)
            count = sum(1 for test in self.metamorphic_tests if test.target_transform is not None)
            return zero, None, {"num_target_mapped_terms": count}

        penalties = []
        names: list[str | None] = []
        target_mapped_terms = 0
        for test in self.metamorphic_tests:
            transformed_batch = test.transform(_clone_batch(batch))
            transformed_prediction = model(transformed_batch).squeeze()
            penalties.append(test.relation.weight * test.relation.penalty(prediction, transformed_prediction))
            names.append(test.name)
            if test.target_transform is not None:
                target_mapped_terms += 1

        penalties_tensor = torch.stack(penalties) if penalties else prediction.new_zeros(0)
        if not penalties:
            return prediction.new_tensor(0.0), None, {"num_target_mapped_terms": target_mapped_terms}
        if self.relation_aggregation == "max":
            relation_penalty, worst_idx_tensor = torch.max(penalties_tensor, dim=0)
            worst_idx = int(worst_idx_tensor.item()) if worst_idx_tensor.dim() == 0 else int(
                worst_idx_tensor.reshape(-1)[0])
        else:
            relation_penalty = torch.mean(penalties_tensor)
            worst_idx = int(torch.argmax(penalties_tensor).item())
        return relation_penalty, names[worst_idx], {"num_target_mapped_terms": target_mapped_terms}

    def _compute_relation_target_mapped_supervised(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
    ) -> torch.Tensor:
        if not self.metamorphic_tests or self.target_mapped_weight == 0:
            if torch.is_tensor(target):
                return target.new_tensor(0.0)
            return torch.tensor(0.0)

        mapped_losses = []
        for test in self.metamorphic_tests:
            if test.target_transform is None:
                continue
            transformed_batch = test.transform(_clone_batch(batch))
            transformed_prediction = model(transformed_batch).squeeze()
            transformed_target = _apply_target_transform(
                test.target_transform,
                target=target,
                source_batch=batch,
                transformed_batch=transformed_batch,
            )
            mapped_losses.append(self.supervised_loss(transformed_prediction, transformed_target))

        if not mapped_losses:
            return target.new_tensor(0.0)
        mapped_losses_tensor = torch.stack(mapped_losses)
        if self.target_mapped_aggregation == "max":
            return torch.max(mapped_losses_tensor)
        return torch.mean(mapped_losses_tensor)

    def _compute_paper_worst_case_loss(
            self,
            model: nn.Module,
            batch: Batch,
            target: torch.Tensor,
    ) -> tuple[torch.Tensor, str | None]:
        if not self.transform_set or self.paper_worst_case_weight == 0:
            if torch.is_tensor(target):
                return target.new_tensor(0.0), None
            return torch.tensor(0.0), None

        transformed_losses = []
        transform_names: list[str | None] = []
        for transform_spec in self.transform_set:
            transformed_batch = transform_spec.transform(_clone_batch(batch))
            transformed_prediction = model(transformed_batch).squeeze()
            transformed_target = _apply_target_transform(
                transform_spec.target_transform,
                target=target,
                source_batch=batch,
                transformed_batch=transformed_batch,
            )
            transformed_losses.append(self.supervised_loss(transformed_prediction, transformed_target))
            transform_names.append(transform_spec.name)

        if not transformed_losses:
            return target.new_tensor(0.0), None
        transformed_losses_tensor = torch.stack(transformed_losses)
        worst_loss, worst_idx_tensor = torch.max(transformed_losses_tensor, dim=0)
        worst_idx = int(worst_idx_tensor.item()) if worst_idx_tensor.dim() == 0 else int(
            worst_idx_tensor.reshape(-1)[0])
        return worst_loss, transform_names[worst_idx]


def _clone_batch(batch: Batch) -> Batch:
    cloned: Batch = {}
    for key, value in batch.items():
        if torch.is_tensor(value):
            cloned[key] = value.clone()
        else:
            cloned[key] = deepcopy(value)
    return cloned


def _clone_target(target: torch.Tensor) -> torch.Tensor:
    return target.clone() if torch.is_tensor(target) else deepcopy(target)


def _apply_target_transform(
        target_transform: TargetTransform | None,
        target: torch.Tensor,
        source_batch: Batch,
        transformed_batch: Batch,
) -> torch.Tensor:
    if target_transform is None:
        return target
    cloned_target = _clone_target(target)
    try:
        return target_transform(cloned_target, source_batch, transformed_batch)
    except TypeError:
        return target_transform(cloned_target)
