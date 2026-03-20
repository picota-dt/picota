from __future__ import annotations

import copy
from typing import Iterable

import torch

from picota.framework.control.MetamorphicEvaluation import MetamorphicEvaluation
from picota.framework.control.kan.KAN import KAN
from picota.framework.control.kan.MetamorphicLoss import CompositeMetamorphicLoss
from picota.framework.control.training.KanBaselineTrainer import KanBaselineTrainer


class KanMetamorphicTrainer(KanBaselineTrainer):
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
            rule_specs: Iterable[object] | None,
            supervised_weight: float,
            relation_constraint_weight: float,
            worst_case_over_t_weight: float,
    ):
        super().__init__(
            name=name,
            input_variables=input_variables,
            output_variable=output_variable,
            lookback=lookback,
            means=means,
            stds=stds,
            out_min=out_min,
            out_max=out_max,
            batch_size=batch_size,
            epochs=epochs,
            device=device,
            learning_rate=learning_rate,
            seed=seed,
        )
        self.loss_fn = CompositeMetamorphicLoss.from_rule_specs(
            rule_specs=rule_specs,
            supervised_loss=torch.nn.MSELoss(),
            supervised_weight=float(supervised_weight),
            relation_constraint_weight=float(relation_constraint_weight),
            worst_case_over_T_weight=float(worst_case_over_t_weight),
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
                loss = self.loss_fn.compute_training_loss(
                    model=model,
                    batch=batch,
                    target=out,
                    prediction=pred,
                )
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

    def evaluate_with_rule_violations(
            self,
            model: KAN,
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


__all__ = ["KanMetamorphicTrainer"]
