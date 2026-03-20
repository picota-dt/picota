from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class MetamorphicConfig:
    enabled: bool = False
    supervised_weight: float = 1.0
    relation_constraint_weight: float = 0.25
    worst_case_over_t_weight: float = 0.0
    violation_atol: float = 1e-6
    violation_rtol: float = 1e-4
    rule_specs: list[dict[str, Any]] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None) -> "MetamorphicConfig":
        data = data or {}
        raw_rule_specs = data.get("rule_specs") or []
        if not isinstance(raw_rule_specs, list):
            raise TrainingConfigError("metamorphic.rule_specs must be a list")
        parsed_rule_specs: list[dict[str, Any]] = []
        for index, item in enumerate(raw_rule_specs):
            if not isinstance(item, dict):
                raise TrainingConfigError(f"metamorphic.rule_specs[{index}] must be an object")
            parsed_rule_specs.append(dict(item))
        return cls(
            enabled=bool(data.get("enabled", False)),
            supervised_weight=FieldParser.readFloat(
                data.get("supervised_weight", 1.0),
                fieldName="metamorphic.supervised_weight",
                minimum=0.0,
            ),
            relation_constraint_weight=FieldParser.readFloat(
                data.get("relation_constraint_weight", 0.25),
                fieldName="metamorphic.relation_constraint_weight",
                minimum=0.0,
            ),
            worst_case_over_t_weight=FieldParser.readFloat(
                data.get("worst_case_over_t_weight", 0.0),
                fieldName="metamorphic.worst_case_over_t_weight",
                minimum=0.0,
            ),
            violation_atol=FieldParser.readFloat(
                data.get("violation_atol", 1e-6),
                fieldName="metamorphic.violation_atol",
                minimum=0.0,
            ),
            violation_rtol=FieldParser.readFloat(
                data.get("violation_rtol", 1e-4),
                fieldName="metamorphic.violation_rtol",
                minimum=0.0,
            ),
            rule_specs=parsed_rule_specs,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "supervised_weight": self.supervised_weight,
            "relation_constraint_weight": self.relation_constraint_weight,
            "worst_case_over_t_weight": self.worst_case_over_t_weight,
            "violation_atol": self.violation_atol,
            "violation_rtol": self.violation_rtol,
            "rule_specs": [dict(spec) for spec in self.rule_specs],
        }


__all__ = ["MetamorphicConfig"]
