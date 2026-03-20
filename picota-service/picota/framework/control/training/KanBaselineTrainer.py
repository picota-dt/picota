from __future__ import annotations

import copy
import random

import numpy as np
import torch
from torch.utils.data import DataLoader

from picota.framework.control.kan.KAN import KAN
from picota.framework.control.kan.TimeSeriesDataset import TimeSeriesDataset
from picota.framework.control.training.EvalMetrics import EvalMetrics


class KanBaselineTrainer:
    def __init__(
            self,
            *,
            name: str,
            input_variables: list[str],
            output_variable: str,
            lookback: int,
            means: list[float],
            stds: list[float],
            out_min: float,
            out_max: float,
            batch_size: int,
            epochs: int,
            device,
            learning_rate: float,
            seed: int,
    ):
        self.name = name
        self.input_variables = list(input_variables)
        self.output_variable = output_variable
        self.lookback = int(lookback)
        self.means = list(means)
        self.stds = list(stds)
        self.out_min = float(out_min)
        self.out_max = float(out_max)
        self.batch_size = int(batch_size)
        self.epochs = int(epochs)
        self.device = device
        self.learning_rate = float(learning_rate)
        self.seed = int(seed)
        self.loss_fn = torch.nn.MSELoss()
        self.last_validation_metrics: dict[str, float | int | str] | None = None

    def _set_seed(self) -> None:
        random.seed(self.seed)
        np.random.seed(self.seed)
        torch.manual_seed(self.seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(self.seed)

    def _raw_span_or_one(self) -> float:
        span = self.out_max - self.out_min
        return span if span > 0 else 1.0

    def _make_loader(self, items: list[dict], shuffle: bool) -> DataLoader:
        return DataLoader(TimeSeriesDataset(items), batch_size=self.batch_size, shuffle=shuffle)

    def _evaluate_loader(self, model: KAN, loader: DataLoader) -> EvalMetrics:
        model.eval()
        preds: list[torch.Tensor] = []
        targets: list[torch.Tensor] = []
        with torch.no_grad():
            for batch in loader:
                pred = model(batch).squeeze().reshape(-1)
                out = batch["out"].reshape(-1)
                preds.append(pred.detach().to(self.device))
                targets.append(out.detach().to(self.device))
        if not preds:
            return EvalMetrics(
                n_samples=0,
                output_scale="normalized",
                mae_model=float("inf"),
                rmse_model=float("inf"),
                r2=float("nan"),
                mae_raw=float("inf"),
                rmse_raw=float("inf"),
                max_abs_err_raw=float("inf"),
                p95_abs_err_raw=float("inf"),
                p99_abs_err_raw=float("inf"),
                tail5_mean_abs_err_raw=float("inf"),
            )
        y_pred = torch.cat(preds)
        y_true = torch.cat(targets)
        err = y_pred - y_true
        mae_model = float(torch.mean(torch.abs(err)).item())
        rmse_model = float(torch.sqrt(torch.mean(err ** 2)).item())
        ss_res = float(torch.sum(err ** 2).item())
        ss_tot = float(torch.sum((y_true - torch.mean(y_true)) ** 2).item())
        r2 = float("nan") if ss_tot == 0.0 else float(1.0 - (ss_res / ss_tot))
        raw_span = self._raw_span_or_one()
        mae_raw = float(mae_model * raw_span)
        rmse_raw = float(rmse_model * raw_span)
        abs_err_raw = torch.abs(err) * raw_span
        max_abs_err_raw = float(torch.max(abs_err_raw).item())
        p95_abs_err_raw = float(torch.quantile(abs_err_raw, 0.95).item())
        p99_abs_err_raw = float(torch.quantile(abs_err_raw, 0.99).item())
        sample_count = int(abs_err_raw.numel())
        tail_k = max(1, (sample_count + 19) // 20)
        tail5_mean_abs_err_raw = float(torch.topk(abs_err_raw, k=tail_k).values.mean().item())
        return EvalMetrics(
            n_samples=int(y_true.numel()),
            output_scale="normalized",
            mae_model=mae_model,
            rmse_model=rmse_model,
            r2=r2,
            mae_raw=mae_raw,
            rmse_raw=rmse_raw,
            max_abs_err_raw=max_abs_err_raw,
            p95_abs_err_raw=p95_abs_err_raw,
            p99_abs_err_raw=p99_abs_err_raw,
            tail5_mean_abs_err_raw=tail5_mean_abs_err_raw,
        )

    def train(self, train_items: list[dict], val_items: list[dict]) -> tuple[KAN, dict[str, float | int | str]]:
        if len(train_items) == 0 or len(val_items) == 0:
            raise ValueError("train_items and val_items must be non-empty")
        self._set_seed()
        model = KAN(
            input_features=len(self.input_variables),
            lookback_size=self.lookback,
            means=self.means,
            stds=self.stds,
            output_features=1,
        ).to(self.device)
        optimizer = torch.optim.Adam(model.parameters(), lr=self.learning_rate)
        train_loader = self._make_loader(train_items, shuffle=True)
        val_loader = self._make_loader(val_items, shuffle=False)

        best_state = copy.deepcopy(model.state_dict())
        best_val_mae = float("inf")
        best_metrics: dict[str, float | int | str] | None = None
        for _ in range(self.epochs):
            model.train()
            for batch in train_loader:
                out = batch["out"]
                optimizer.zero_grad()
                pred = model(batch).squeeze()
                loss = self.loss_fn(pred, out)
                loss.backward()
                optimizer.step()
            val_metrics = self._evaluate_loader(model, val_loader)
            if val_metrics.mae_model < best_val_mae:
                best_val_mae = val_metrics.mae_model
                best_state = copy.deepcopy(model.state_dict())
                best_metrics = val_metrics.as_dict()
        model.load_state_dict(best_state)
        if best_metrics is None:
            best_metrics = self._evaluate_loader(model, val_loader).as_dict()
        self.last_validation_metrics = best_metrics
        return model, best_metrics

    def evaluate(self, model: KAN, items: list[dict]) -> dict[str, float | int | str]:
        if len(items) == 0:
            raise ValueError("items must be non-empty")
        loader = self._make_loader(items, shuffle=False)
        return self._evaluate_loader(model, loader).as_dict()


__all__ = ["KanBaselineTrainer"]
