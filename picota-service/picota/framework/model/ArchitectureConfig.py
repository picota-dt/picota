from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.TrainingConfigError import TrainingConfigError


@dataclass(frozen=True)
class ArchitectureConfig:
    family: str = "kan"
    mode: str = "baseline"
    epochs: int = 50
    batch_size: int = 32
    learning_rate: float = 5e-4
    seed: int = 42
    tabnet_n_steps: int = 4
    tabnet_n_d: int = 24
    tabnet_n_a: int = 24
    tabnet_gamma: float = 1.3
    tabnet_dropout: float = 0.05
    tabnet_mask_temperature: float = 1.0

    @classmethod
    def from_dict(cls, data: dict[str, Any] | None) -> "ArchitectureConfig":
        data = data or {}
        family = str(data.get("family", "kan")).strip().lower()
        mode = str(data.get("mode", "baseline")).strip().lower()
        if family not in {"kan", "tabnet"}:
            raise TrainingConfigError(f"Unsupported architecture.family '{family}'")
        if mode not in {"baseline", "metamorphic"}:
            raise TrainingConfigError(f"Unsupported architecture.mode '{mode}'")
        return cls(
            family=family,
            mode=mode,
            epochs=FieldParser.readInt(data.get("epochs", 50), fieldName="architecture.epochs", minimum=1),
            batch_size=FieldParser.readInt(data.get("batch_size", 32), fieldName="architecture.batch_size", minimum=1),
            learning_rate=FieldParser.readFloat(
                data.get("learning_rate", 5e-4),
                fieldName="architecture.learning_rate",
                minimum=0.0,
            ),
            seed=FieldParser.readInt(data.get("seed", 42), fieldName="architecture.seed"),
            tabnet_n_steps=FieldParser.readInt(
                data.get("tabnet_n_steps", 4),
                fieldName="architecture.tabnet_n_steps",
                minimum=1,
            ),
            tabnet_n_d=FieldParser.readInt(data.get("tabnet_n_d", 24), fieldName="architecture.tabnet_n_d", minimum=1),
            tabnet_n_a=FieldParser.readInt(data.get("tabnet_n_a", 24), fieldName="architecture.tabnet_n_a", minimum=1),
            tabnet_gamma=FieldParser.readFloat(data.get("tabnet_gamma", 1.3), fieldName="architecture.tabnet_gamma",
                                               minimum=0.0),
            tabnet_dropout=FieldParser.readFloat(
                data.get("tabnet_dropout", 0.05),
                fieldName="architecture.tabnet_dropout",
                minimum=0.0,
            ),
            tabnet_mask_temperature=FieldParser.readFloat(
                data.get("tabnet_mask_temperature", 1.0),
                fieldName="architecture.tabnet_mask_temperature",
                minimum=1e-9,
            ),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "family": self.family,
            "mode": self.mode,
            "epochs": self.epochs,
            "batch_size": self.batch_size,
            "learning_rate": self.learning_rate,
            "seed": self.seed,
            "tabnet_n_steps": self.tabnet_n_steps,
            "tabnet_n_d": self.tabnet_n_d,
            "tabnet_n_a": self.tabnet_n_a,
            "tabnet_gamma": self.tabnet_gamma,
            "tabnet_dropout": self.tabnet_dropout,
            "tabnet_mask_temperature": self.tabnet_mask_temperature,
        }


__all__ = ["ArchitectureConfig"]
