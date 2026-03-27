from enum import Enum


class MetamorphicRelationKind(str, Enum):
    EQUAL = "Equal"
    MONOTONIC = "Monotonic"
    GREATER = "Greater"
    LOWER = "Lower"
    GREATER_OR_EQUAL = "GreaterOrEqual"
    LOWER_OR_EQUAL = "LowerOrEqual"
    PROPORTIONAL = "Proportional"


__all__ = ["MetamorphicRelationKind"]
