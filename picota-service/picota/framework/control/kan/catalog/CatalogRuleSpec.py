from __future__ import annotations

from dataclasses import dataclass

from picota.framework.control.kan.catalog.RuleCategory import RuleCategory


@dataclass(frozen=True)
class CatalogRuleSpec:
    name: str
    category: RuleCategory
    relation_test: object | None = None
    over_T_transform: object | None = None
    description: str | None = None
    consistency_profile: str | None = None

    def __post_init__(self):
        has_relation = self.relation_test is not None
        has_over_t = self.over_T_transform is not None
        if has_relation == has_over_t:
            raise ValueError(
                "CatalogRuleSpec must define exactly one branch: either relation_test or over_T_transform."
            )
        if has_relation and getattr(self.relation_test, "target_transform", None) is not None:
            raise ValueError(
                "Relation constraints cannot define target_transform. "
                "Use over_T_transform for target-mapped rules."
            )


__all__ = ["CatalogRuleSpec"]
