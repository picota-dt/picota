from __future__ import annotations

import copy
import logging
from collections.abc import Callable

import torch

from picota.framework.control.training.KanBaselineTrainer import KanBaselineTrainer
from picota.framework.control.training.TabNetRegressor import TabNetRegressor

logger = logging.getLogger(__name__)


class TabNetBaselineTrainer(KanBaselineTrainer):
    def __init__(
            self,
            *,
            name: str,
            out_min: float,
            out_max: float,
            batch_size: int,
            epochs: int,
            device,
            learning_rate: float,
            seed: int,
            input_dim: int,
            n_d: int = 24,
            n_a: int = 24,
            n_steps: int = 4,
            gamma: float = 1.3,
            dropout: float = 0.05,
            mask_temperature: float = 1.0,
            epoch_progress_listener: Callable[[int, int], None] | None = None,
    ):
        super().__init__(
            name=name,
            input_variables=[f"x{i}" for i in range(int(input_dim))],
            output_variable="out",
            lookback=0,
            means=[],
            stds=[],
            out_min=out_min,
            out_max=out_max,
            batch_size=batch_size,
            epochs=epochs,
            device=device,
            learning_rate=learning_rate,
            seed=seed,
            epoch_progress_listener=epoch_progress_listener,
        )
        self.input_dim = int(input_dim)
        self.n_d = int(n_d)
        self.n_a = int(n_a)
        self.n_steps = int(n_steps)
        self.gamma = float(gamma)
        self.dropout = float(dropout)
        self.mask_temperature = float(mask_temperature)

    def train(
            self,
            train_items: list[dict],
            val_items: list[dict],
    ) -> tuple[TabNetRegressor, dict[str, float | int | str]]:
        if len(train_items) == 0 or len(val_items) == 0:
            raise ValueError("train_items and val_items must be non-empty")
        logger.info(
            "TabNet baseline training started (name=%s, epochs=%s, batch_size=%s)",
            self.name,
            self.epochs,
            self.batch_size,
        )
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
        optimizer = torch.optim.Adam(model.parameters(), lr=self.learning_rate)
        train_loader = self._make_loader(train_items, shuffle=True)
        val_loader = self._make_loader(val_items, shuffle=False)

        best_state = copy.deepcopy(model.state_dict())
        best_val_mae = float("inf")
        best_metrics: dict[str, float | int | str] | None = None
        for epoch in range(1, self.epochs + 1):
            model.train()
            epoch_loss_sum = 0.0
            epoch_batches = 0
            for batch in train_loader:
                out = batch["out"]
                optimizer.zero_grad()
                pred = model(batch).squeeze()
                loss = self.loss_fn(pred, out)
                loss.backward()
                optimizer.step()
                epoch_loss_sum += float(loss.item())
                epoch_batches += 1
            val_metrics = self._evaluate_loader(model, val_loader)
            train_loss = (epoch_loss_sum / float(epoch_batches)) if epoch_batches > 0 else float("nan")
            logger.info(
                "TabNet baseline epoch %s/%s finished (train_loss=%s, val_mae=%s, val_rmse=%s)",
                epoch,
                self.epochs,
                train_loss,
                val_metrics.mae_model,
                val_metrics.rmse_model,
            )
            self._notify_epoch_progress(epoch)
            if val_metrics.mae_model < best_val_mae:
                best_val_mae = val_metrics.mae_model
                best_state = copy.deepcopy(model.state_dict())
                best_metrics = val_metrics.as_dict()
        model.load_state_dict(best_state)
        if best_metrics is None:
            best_metrics = self._evaluate_loader(model, val_loader).as_dict()
        self.last_validation_metrics = best_metrics
        logger.info("TabNet baseline training completed (best_val_mae=%s)", best_metrics.get("mae_model"))
        return model, best_metrics

    def evaluate(
            self,
            model: TabNetRegressor,
            items: list[dict],
    ) -> dict[str, float | int | str]:
        if len(items) == 0:
            raise ValueError("items must be non-empty")
        loader = self._make_loader(items, shuffle=False)
        return self._evaluate_loader(model, loader).as_dict()


__all__ = ["TabNetBaselineTrainer"]
