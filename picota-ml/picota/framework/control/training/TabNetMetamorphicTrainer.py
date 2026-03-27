from __future__ import annotations

import copy
import logging
from collections.abc import Callable
from typing import Iterable

import torch

from picota.framework.control.MetamorphicEvaluation import MetamorphicEvaluation
from picota.framework.control.kan.MetamorphicLoss import CompositeMetamorphicLoss
from picota.framework.control.training.TabNetBaselineTrainer import TabNetBaselineTrainer
from picota.framework.control.training.TabNetRegressor import TabNetRegressor

logger = logging.getLogger(__name__)


class TabNetMetamorphicTrainer(TabNetBaselineTrainer):
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
            rule_specs: Iterable[object] | None = None,
            supervised_weight: float = 1.0,
            relation_constraint_weight: float = 0.25,
            worst_case_over_t_weight: float = 0.0,
            epoch_progress_listener: Callable[[int, int], None] | None = None,
    ):
        super().__init__(
            name=name,
            out_min=out_min,
            out_max=out_max,
            batch_size=batch_size,
            epochs=epochs,
            device=device,
            learning_rate=learning_rate,
            seed=seed,
            input_dim=input_dim,
            n_d=n_d,
            n_a=n_a,
            n_steps=n_steps,
            gamma=gamma,
            dropout=dropout,
            mask_temperature=mask_temperature,
            epoch_progress_listener=epoch_progress_listener,
        )
        self.loss_fn = CompositeMetamorphicLoss.from_rule_specs(
            rule_specs=rule_specs,
            supervised_loss=torch.nn.MSELoss(),
            supervised_weight=float(supervised_weight),
            relation_constraint_weight=float(relation_constraint_weight),
            worst_case_over_T_weight=float(worst_case_over_t_weight),
        )

    def train(
            self,
            train_items: list[dict],
            val_items: list[dict],
    ) -> tuple[TabNetRegressor, dict[str, float | int | str]]:
        if len(train_items) == 0 or len(val_items) == 0:
            raise ValueError("train_items and val_items must be non-empty")
        logger.info(
            "TabNet metamorphic training started (name=%s, epochs=%s, batch_size=%s)",
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
                loss = self.loss_fn.compute_training_loss(
                    model=model,
                    batch=batch,
                    target=out,
                    prediction=pred,
                )
                loss.backward()
                optimizer.step()
                epoch_loss_sum += float(loss.item())
                epoch_batches += 1
            val_metrics = self._evaluate_loader(model, val_loader)
            train_loss = (epoch_loss_sum / float(epoch_batches)) if epoch_batches > 0 else float("nan")
            logger.info(
                "TabNet metamorphic epoch %s/%s finished (train_loss=%s, val_mae=%s, val_rmse=%s)",
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
        logger.info("TabNet metamorphic training completed (best_val_mae=%s)", best_metrics.get("mae_model"))
        return model, best_metrics

    def evaluate_with_rule_violations(
            self,
            model: TabNetRegressor,
            items: list[dict],
            atol: float = 1e-6,
            rtol: float = 1e-4,
    ) -> tuple[dict[str, float | int | str], dict | None]:
        metrics = self.evaluate(model, items)
        relation_tests = list(getattr(self.loss_fn, "assigned_relation_constraints", []) or [])
        if not relation_tests:
            return metrics, None
        loader = self._make_loader(items, shuffle=False)
        report = MetamorphicEvaluation.computeViolationReport(
            model=model,
            data_loader=loader,
            metamorphic_tests=relation_tests,
            atol=float(atol),
            rtol=float(rtol),
        )
        return metrics, report


__all__ = ["TabNetMetamorphicTrainer"]
