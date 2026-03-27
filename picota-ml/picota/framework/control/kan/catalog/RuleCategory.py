from enum import Enum


class RuleCategory(str, Enum):
    INVARIANCE = "invariance"
    DIRECTIONAL_ORDINAL = "directional_ordinal"
    TARGET_MAPPED = "target_mapped"


__all__ = ["RuleCategory"]
