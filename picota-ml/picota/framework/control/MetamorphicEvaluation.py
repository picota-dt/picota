from __future__ import annotations

from copy import deepcopy
from typing import Iterable

import torch

from picota.framework.control.kan.MetamorphicLoss import (
    Equal,
    Greater,
    GreaterOrEqual,
    Lower,
    LowerOrEqual,
    MetamorphicRelation,
    MetamorphicTest,
    Monotonic,
    Proportional,
)

Batch = dict[str, torch.Tensor]


class MetamorphicEvaluation:
    @staticmethod
    def cloneBatch(batch: Batch) -> Batch:
        copied: Batch = {}
        for key, value in batch.items():
            copied[key] = value.clone() if torch.is_tensor(value) else deepcopy(value)
        return copied

    @staticmethod
    def computeViolationReport(
            model,
            data_loader,
            metamorphic_tests: Iterable[MetamorphicTest],
            atol: float = 1e-6,
            rtol: float = 1e-4,
    ) -> dict:
        model.eval()
        tests = list(metamorphic_tests)
        per_test: dict[str, dict[str, float | int]] = {}

        with torch.no_grad():
            for batch in data_loader:
                base_pred = model(batch).squeeze().reshape(-1)
                for index, test in enumerate(tests):
                    test_name = test.name or f"{test.relation.kind.value}_{index}"
                    transformed = test.transform(MetamorphicEvaluation.cloneBatch(batch))
                    transformed_pred = model(transformed).squeeze().reshape(-1)
                    local_atol = atol if test.violation_atol is None else float(test.violation_atol)
                    local_rtol = rtol if test.violation_rtol is None else float(test.violation_rtol)
                    violations = MetamorphicEvaluation.violationMask(
                        relation=test.relation,
                        base_pred=base_pred,
                        transformed_pred=transformed_pred,
                        atol=local_atol,
                        rtol=local_rtol,
                    )
                    if test_name not in per_test:
                        per_test[test_name] = {"violations": 0, "total": 0}
                    per_test[test_name]["violations"] += int(violations.sum().item())
                    per_test[test_name]["total"] += int(violations.numel())

        total_violations = 0
        total_cases = 0
        for stats in per_test.values():
            violations = int(stats["violations"])
            total = int(stats["total"])
            stats["violation_rate"] = (violations / total) if total else float("nan")
            total_violations += violations
            total_cases += total

        return {
            "overall_violation_rate": (total_violations / total_cases) if total_cases else float("nan"),
            "total_violations": total_violations,
            "total_cases": total_cases,
            "by_test": per_test,
        }

    @staticmethod
    def violationMask(
            relation: MetamorphicRelation,
            base_pred: torch.Tensor,
            transformed_pred: torch.Tensor,
            atol: float,
            rtol: float,
    ) -> torch.Tensor:
        def tol(reference: torch.Tensor) -> torch.Tensor:
            return atol + rtol * torch.abs(reference)

        def to_relation_space(prediction: torch.Tensor) -> torch.Tensor:
            raw_out_min = getattr(relation, "raw_out_min", None)
            raw_out_max = getattr(relation, "raw_out_max", None)
            if raw_out_min is None or raw_out_max is None or not raw_out_max > raw_out_min:
                return prediction
            span = float(raw_out_max) - float(raw_out_min)
            return prediction * span + float(raw_out_min)

        if isinstance(relation, Equal):
            diff = torch.abs(transformed_pred - base_pred)
            return diff > tol(base_pred)
        if isinstance(relation, Greater):
            threshold = base_pred + getattr(relation, "margin", 0.0)
            return transformed_pred <= (threshold + tol(threshold))
        if isinstance(relation, GreaterOrEqual):
            threshold = base_pred + getattr(relation, "margin", 0.0)
            return transformed_pred < (threshold - tol(threshold))
        if isinstance(relation, Lower):
            threshold = base_pred - getattr(relation, "margin", 0.0)
            return transformed_pred >= (threshold - tol(threshold))
        if isinstance(relation, LowerOrEqual):
            threshold = base_pred - getattr(relation, "margin", 0.0)
            return transformed_pred > (threshold + tol(threshold))
        if isinstance(relation, Monotonic):
            direction = getattr(relation, "direction", "increasing")
            margin = getattr(relation, "margin", 0.0)
            if direction == "increasing":
                threshold = base_pred + margin
                return transformed_pred < (threshold - tol(threshold))
            threshold = base_pred - margin
            return transformed_pred > (threshold + tol(threshold))
        if isinstance(relation, Proportional):
            expected = to_relation_space(base_pred) * getattr(relation, "factor", 1.0)
            diff = torch.abs(to_relation_space(transformed_pred) - expected)
            return diff > tol(expected)
        raise TypeError(f"Unsupported relation type for violation check: {type(relation).__name__}")
