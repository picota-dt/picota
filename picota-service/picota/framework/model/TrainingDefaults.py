from __future__ import annotations

from picota.framework.model.TimeHorizon import TimeHorizon


class TrainingDefaults:
    DEFAULT_TIME_HORIZON = TimeHorizon(value=1, unit="steps")
    DEFAULT_TARGET_VARIABLE = "target"
