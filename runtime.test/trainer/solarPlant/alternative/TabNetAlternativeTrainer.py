from __future__ import annotations

import copy
import random
from dataclasses import dataclass

import numpy as np
import torch
from kan.TimeSeriesDataset import TimeSeriesDataset
from torch.utils.data import DataLoader

from trainer.solarPlant.alternative.TabNetModel import TabNetRegressor


@dataclass
class EvalMetrics:
    n_samples: int
    output_scale: str
    mae_model: float
    rmse_model: float
    r2: float
    mae_raw: float
    rmse_raw: float

    def as_dict(self) -> dict[str, float | int | str]:
        return {
            "n_samples": self.n_samples,
            "output_scale": self.output_scale,
            "mae_model": self.mae_model,
            "rmse_model": self.rmse_model,
            "r2": self.r2,
            "mae_raw": self.mae_raw,
            "rmse_raw": self.rmse_raw,
        }


class TabNetAlternativeTrainer:
    def __init__(
            self,
            name: str,
            out_min: float,
            out_max: float,
            batch_size: int,
            epochs: int,
            device,
            lr: float,
            seed: int,
            input_dim: int,
            n_d: int = 24,
            n_a: int = 24,
            n_steps: int = 4,
            gamma: float = 1.3,
            dropout: float = 0.05,
            mask_temperature: float = 1.0,
    ):
        self.name = name
        self.out_min = float(out_min)
        self.out_max = float(out_max)
        self.batch_size = int(batch_size)
        self.epochs = int(epochs)
        self.device = device
        self.lr = float(lr)
        self.seed = int(seed)
        self.input_dim = int(input_dim)
        self.n_d = int(n_d)
        self.n_a = int(n_a)
        self.n_steps = int(n_steps)
        self.gamma = float(gamma)
        self.dropout = float(dropout)
        self.mask_temperature = float(mask_temperature)
        self.criterion = torch.nn.MSELoss()
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

    def _evaluate_loader(self, model: TabNetRegressor, data_loader: DataLoader) -> EvalMetrics:
        model.eval()
        preds = []
        targets = []
        with torch.no_grad():
            for batch in data_loader:
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

        return EvalMetrics(
            n_samples=int(y_true.numel()),
            output_scale="normalized",
            mae_model=mae_model,
            rmse_model=rmse_model,
            r2=r2,
            mae_raw=mae_raw,
            rmse_raw=rmse_raw,
        )

    def train(self, train_items: list[dict], val_items: list[dict]) -> tuple[
        TabNetRegressor, dict[str, float | int | str]]:
        if len(train_items) == 0 or len(val_items) == 0:
            raise ValueError("train_items and val_items must be non-empty")

        self._set_seed()
        model = TabNetRegressor(
            input_dim=self.input_dim,
            output_dim=1,
            n_d=self.n_d,
            n_a=self.n_a,
            n_steps=self.n_steps,
            gamma=self.gamma,
            dropout=self.dropout,
            mask_temperature=self.mask_temperature,
        ).to(self.device)
        optimizer = torch.optim.Adam(model.parameters(), lr=self.lr)
        train_loader = self._make_loader(train_items, shuffle=True)
        val_loader = self._make_loader(val_items, shuffle=False)

        best_state = copy.deepcopy(model.state_dict())
        best_val_mae = float("inf")
        best_val_metrics: dict[str, float | int | str] | None = None

        for epoch in range(self.epochs):
            model.train()
            total_loss = 0.0
            total_count = 0
            for batch in train_loader:
                out = batch["out"]
                optimizer.zero_grad()
                pred = model(batch).squeeze()
                loss = self.criterion(pred, out)
                loss.backward()
                optimizer.step()

                batch_size = int(out.shape[0] if out.dim() > 0 else 1)
                total_loss += float(loss.detach().item()) * batch_size
                total_count += batch_size

            val_metrics = self._evaluate_loader(model, val_loader)
            train_loss = total_loss / max(1, total_count)
            print(
                f"{self.name}\tepoch={epoch + 1}/{self.epochs}\t"
                f"train_loss={train_loss:.6f}\t"
                f"val_mae={val_metrics.mae_model:.6f}\t"
                f"val_rmse={val_metrics.rmse_model:.6f}\t"
                f"val_r2={val_metrics.r2:.6f}",
                flush=True,
            )
            if val_metrics.mae_model < best_val_mae:
                best_val_mae = val_metrics.mae_model
                best_state = copy.deepcopy(model.state_dict())
                best_val_metrics = val_metrics.as_dict()

        model.load_state_dict(best_state)
        self.last_validation_metrics = best_val_metrics
        if best_val_metrics is None:
            best_val_metrics = self._evaluate_loader(model, val_loader).as_dict()
            self.last_validation_metrics = best_val_metrics

        print(
            f"{self.name}\tbest_val_n={best_val_metrics['n_samples']}\t"
            f"best_val_mae={float(best_val_metrics['mae_model']):.6f}\t"
            f"best_val_rmse={float(best_val_metrics['rmse_model']):.6f}\t"
            f"best_val_r2={float(best_val_metrics['r2']):.6f}\t"
            f"best_val_mae_raw={float(best_val_metrics['mae_raw']):.6f}\t"
            f"best_val_rmse_raw={float(best_val_metrics['rmse_raw']):.6f}",
            flush=True,
        )
        return model, best_val_metrics

    def evaluate(self, model: TabNetRegressor, items: list[dict]) -> dict[str, float | int | str]:
        if len(items) == 0:
            raise ValueError("items must be non-empty")
        loader = self._make_loader(items, shuffle=False)
        metrics = self._evaluate_loader(model, loader).as_dict()
        return metrics
