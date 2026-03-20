from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class RuleBuildContext:
    feature_names: dict[str, list[str]]

    def resolve_index(self, field: str, spec: dict[str, Any], *, key_prefix: str) -> int:
        if "index" in spec:
            try:
                return int(spec["index"])
            except (TypeError, ValueError) as exc:
                raise ValueError(f"{key_prefix}.index must be an integer") from exc
        feature_name = spec.get("feature")
        if feature_name is None:
            raise ValueError(f"{key_prefix} requires 'index' or 'feature'")
        names = self.feature_names.get(field) or []
        if feature_name not in names:
            raise ValueError(
                f"{key_prefix}.feature '{feature_name}' not found in field '{field}' names={names}"
            )
        return names.index(str(feature_name))


__all__ = ["RuleBuildContext"]
