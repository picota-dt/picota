from __future__ import annotations

from dataclasses import dataclass


@dataclass
class EvalMetrics:
    n_samples: int
    output_scale: str
    mae_model: float
    rmse_model: float
    r2: float
    mae_raw: float
    rmse_raw: float
    max_abs_err_raw: float
    p95_abs_err_raw: float
    p99_abs_err_raw: float
    tail5_mean_abs_err_raw: float

    def as_dict(self) -> dict[str, float | int | str]:
        return {
            "n_samples": self.n_samples,
            "output_scale": self.output_scale,
            "mae_model": self.mae_model,
            "rmse_model": self.rmse_model,
            "r2": self.r2,
            "mae_raw": self.mae_raw,
            "rmse_raw": self.rmse_raw,
            "max_abs_err_raw": self.max_abs_err_raw,
            "p95_abs_err_raw": self.p95_abs_err_raw,
            "p99_abs_err_raw": self.p99_abs_err_raw,
            "tail5_mean_abs_err_raw": self.tail5_mean_abs_err_raw,
        }


__all__ = ["EvalMetrics"]
