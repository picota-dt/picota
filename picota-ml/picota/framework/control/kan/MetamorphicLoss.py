from __future__ import annotations

from picota.framework.control.kan.loss.CompositeMetamorphicLoss import CompositeMetamorphicLoss
from picota.framework.control.kan.loss.Equal import Equal
from picota.framework.control.kan.loss.Greater import Greater
from picota.framework.control.kan.loss.GreaterOrEqual import GreaterOrEqual
from picota.framework.control.kan.loss.Lower import Lower
from picota.framework.control.kan.loss.LowerOrEqual import LowerOrEqual
from picota.framework.control.kan.loss.MetamorphicRelation import MetamorphicRelation
from picota.framework.control.kan.loss.MetamorphicRelationKind import MetamorphicRelationKind
from picota.framework.control.kan.loss.MetamorphicTest import MetamorphicTest
from picota.framework.control.kan.loss.MetamorphicTransform import (
    Batch,
    BatchTransform,
    MetamorphicTransform,
    TargetTransform,
)
from picota.framework.control.kan.loss.Monotonic import Monotonic
from picota.framework.control.kan.loss.Proportional import Proportional
from picota.framework.control.kan.loss.TransformSet import TransformSet


def partition_rule_specs_exclusive(rule_specs):
    return CompositeMetamorphicLoss.partitionRuleSpecsExclusive(rule_specs)


class MetamorphicLoss:
    @staticmethod
    def partitionRuleSpecsExclusive(rule_specs):
        return partition_rule_specs_exclusive(rule_specs)


__all__ = [
    "Batch",
    "BatchTransform",
    "TargetTransform",
    "MetamorphicRelationKind",
    "MetamorphicRelation",
    "Equal",
    "Greater",
    "Lower",
    "GreaterOrEqual",
    "LowerOrEqual",
    "Monotonic",
    "Proportional",
    "MetamorphicTransform",
    "TransformSet",
    "MetamorphicTest",
    "CompositeMetamorphicLoss",
    "partition_rule_specs_exclusive",
    "MetamorphicLoss",
]
