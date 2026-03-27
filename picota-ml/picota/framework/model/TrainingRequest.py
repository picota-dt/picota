from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from picota.framework.model.ArchitectureConfig import ArchitectureConfig
from picota.framework.model.DataSourceConfig import DataSourceConfig
from picota.framework.model.FieldParser import FieldParser
from picota.framework.model.MetamorphicConfig import MetamorphicConfig
from picota.framework.model.SplitConfig import SplitConfig
from picota.framework.model.TimeHorizon import TimeHorizon
from picota.framework.model.TrainingConfigError import TrainingConfigError
from picota.framework.model.TrainingDefaults import TrainingDefaults


@dataclass(frozen=True)
class TrainingRequest:
    job_name: str
    data_source: DataSourceConfig
    architecture: ArchitectureConfig
    split: SplitConfig
    metamorphic: MetamorphicConfig
    target_variable: str
    lookback: int
    time_horizon: TimeHorizon
    output_dir: str | None = None
    created_by: str | None = None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "TrainingRequest":
        if not isinstance(data, dict):
            raise TrainingConfigError("Training request payload must be an object")
        data_source = DataSourceConfig.from_dict(data.get("data_source") or {})
        raw_source_default_horizon = data_source.options.get("default_time_horizon")
        default_horizon = TrainingDefaults.DEFAULT_TIME_HORIZON
        if isinstance(raw_source_default_horizon, dict):
            default_horizon = TimeHorizon.from_dict(
                raw_source_default_horizon,
                default_unit=TrainingDefaults.DEFAULT_TIME_HORIZON.unit,
                default_value=TrainingDefaults.DEFAULT_TIME_HORIZON.value,
            )
        time_horizon = TimeHorizon.from_dict(
            data.get("time_horizon") or {},
            default_unit=default_horizon.unit,
            default_value=default_horizon.value,
        )
        source_default_target = data_source.options.get("target_column")
        target_variable = str(
            data.get("target_variable")
            or source_default_target
            or TrainingDefaults.DEFAULT_TARGET_VARIABLE
        ).strip()
        if not target_variable:
            raise TrainingConfigError("target_variable must not be empty")
        lookback = FieldParser.readInt(data.get("lookback", 0), fieldName="lookback", minimum=0)
        job_name = str(data.get("job_name") or f"{data_source.kind}-training").strip()
        if not job_name:
            raise TrainingConfigError("job_name must not be empty")
        output_dir = data.get("output_dir")
        if output_dir is not None:
            output_dir = str(output_dir)
        created_by = data.get("created_by")
        if created_by is not None:
            created_by = str(created_by)
        return cls(
            job_name=job_name,
            data_source=data_source,
            architecture=ArchitectureConfig.from_dict(data.get("architecture")),
            split=SplitConfig.from_dict(data.get("split")),
            metamorphic=MetamorphicConfig.from_dict(data.get("metamorphic")),
            target_variable=target_variable,
            lookback=lookback,
            time_horizon=time_horizon,
            output_dir=output_dir,
            created_by=created_by,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "job_name": self.job_name,
            "data_source": self.data_source.to_dict(),
            "architecture": self.architecture.to_dict(),
            "split": self.split.to_dict(),
            "metamorphic": self.metamorphic.to_dict(),
            "target_variable": self.target_variable,
            "lookback": self.lookback,
            "time_horizon": self.time_horizon.to_dict(),
            "output_dir": self.output_dir,
            "created_by": self.created_by,
        }


__all__ = ["TrainingRequest"]
