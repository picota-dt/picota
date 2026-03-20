from __future__ import annotations

from pathlib import Path
from typing import Any

import torch
from torch.utils.data import DataLoader

from picota.framework.control.Device import Device
from picota.framework.control.MetamorphicEvaluation import MetamorphicEvaluation
from picota.framework.control.adapters.AdapterFactory import AdapterFactory
from picota.framework.control.kan.MetamorphicCatalog import summarize_rule_specs
from picota.framework.control.kan.TimeSeriesDataset import TimeSeriesDataset
from picota.framework.control.rules.ApiRuleBuilder import ApiRuleBuilder
from picota.framework.control.training.KanBaselineTrainer import KanBaselineTrainer
from picota.framework.control.training.KanMetamorphicTrainer import KanMetamorphicTrainer
from picota.framework.control.training.TabNetBaselineTrainer import TabNetBaselineTrainer
from picota.framework.control.training.TabNetMetamorphicTrainer import TabNetMetamorphicTrainer
from picota.framework.model.TrainingRequest import TrainingRequest


class TrainingRunner:
    def run(self, request: TrainingRequest, output_root: Path) -> dict[str, Any]:
        prepared = AdapterFactory.buildPreparedData(request)
        rule_specs = ApiRuleBuilder.buildRuleSpecsFromApi(
            request.metamorphic.rule_specs,
            feature_names=(prepared.metadata.get("feature_names") or {}),
        )
        device = Device.getDevice()
        output_root.mkdir(parents=True, exist_ok=True)
        model_path = output_root / "model.pt"

        use_metamorphic_mode = request.architecture.mode == "metamorphic" or request.metamorphic.enabled
        can_use_metamorphic = use_metamorphic_mode and len(rule_specs) > 0

        if request.architecture.family == "kan":
            if can_use_metamorphic:
                trainer = KanMetamorphicTrainer(
                    name=f"{prepared.case_name}[KAN-Mm]",
                    input_variables=prepared.input_variables,
                    output_variable=prepared.output_variable,
                    lookback=prepared.lookback,
                    means=prepared.means,
                    stds=prepared.stds,
                    out_min=prepared.out_min,
                    out_max=prepared.out_max,
                    batch_size=request.architecture.batch_size,
                    epochs=request.architecture.epochs,
                    device=device,
                    learning_rate=request.architecture.learning_rate,
                    seed=request.architecture.seed,
                    rule_specs=rule_specs,
                    supervised_weight=request.metamorphic.supervised_weight,
                    relation_constraint_weight=request.metamorphic.relation_constraint_weight,
                    worst_case_over_t_weight=request.metamorphic.worst_case_over_t_weight,
                )
                trainer_mode = "metamorphic"
            else:
                trainer = KanBaselineTrainer(
                    name=f"{prepared.case_name}[KAN]",
                    input_variables=prepared.input_variables,
                    output_variable=prepared.output_variable,
                    lookback=prepared.lookback,
                    means=prepared.means,
                    stds=prepared.stds,
                    out_min=prepared.out_min,
                    out_max=prepared.out_max,
                    batch_size=request.architecture.batch_size,
                    epochs=request.architecture.epochs,
                    device=device,
                    learning_rate=request.architecture.learning_rate,
                    seed=request.architecture.seed,
                )
                trainer_mode = "baseline"
        elif request.architecture.family == "tabnet":
            if can_use_metamorphic:
                trainer = TabNetMetamorphicTrainer(
                    name=f"{prepared.case_name}[tabnet-mm]",
                    out_min=prepared.out_min,
                    out_max=prepared.out_max,
                    batch_size=request.architecture.batch_size,
                    epochs=request.architecture.epochs,
                    device=device,
                    learning_rate=request.architecture.learning_rate,
                    seed=request.architecture.seed,
                    input_dim=len(prepared.input_variables),
                    n_d=request.architecture.tabnet_n_d,
                    n_a=request.architecture.tabnet_n_a,
                    n_steps=request.architecture.tabnet_n_steps,
                    gamma=request.architecture.tabnet_gamma,
                    dropout=request.architecture.tabnet_dropout,
                    mask_temperature=request.architecture.tabnet_mask_temperature,
                    rule_specs=rule_specs,
                    supervised_weight=request.metamorphic.supervised_weight,
                    relation_constraint_weight=request.metamorphic.relation_constraint_weight,
                    worst_case_over_t_weight=request.metamorphic.worst_case_over_t_weight,
                )
                trainer_mode = "metamorphic"
            else:
                trainer = TabNetBaselineTrainer(
                    name=f"{prepared.case_name}[tabnet]",
                    out_min=prepared.out_min,
                    out_max=prepared.out_max,
                    batch_size=request.architecture.batch_size,
                    epochs=request.architecture.epochs,
                    device=device,
                    learning_rate=request.architecture.learning_rate,
                    seed=request.architecture.seed,
                    input_dim=len(prepared.input_variables),
                    n_d=request.architecture.tabnet_n_d,
                    n_a=request.architecture.tabnet_n_a,
                    n_steps=request.architecture.tabnet_n_steps,
                    gamma=request.architecture.tabnet_gamma,
                    dropout=request.architecture.tabnet_dropout,
                    mask_temperature=request.architecture.tabnet_mask_temperature,
                )
                trainer_mode = "baseline"
        else:
            raise ValueError(f"Unsupported architecture.family: {request.architecture.family}")

        model, best_val_metrics = trainer.train(prepared.train_items, prepared.val_items)
        if can_use_metamorphic and hasattr(trainer, "evaluate_with_rule_violations"):
            test_metrics, test_violation_report = trainer.evaluate_with_rule_violations(
                model=model,
                items=prepared.test_items,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            val_metrics, val_violation_report = trainer.evaluate_with_rule_violations(
                model=model,
                items=prepared.val_items,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
        else:
            test_metrics = trainer.evaluate(model, prepared.test_items)
            val_metrics = trainer.evaluate(model, prepared.val_items)
            val_violation_report = self._evaluateRuleViolations(
                model=model,
                items=prepared.val_items,
                rule_specs=rule_specs,
                batch_size=request.architecture.batch_size,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            test_violation_report = self._evaluateRuleViolations(
                model=model,
                items=prepared.test_items,
                rule_specs=rule_specs,
                batch_size=request.architecture.batch_size,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )

        torch.save(model.state_dict(), model_path)
        if rule_specs:
            rule_summary = summarize_rule_specs(rule_specs)
        else:
            rule_summary = {
                "num_specs": 0,
                "num_relation_tests": 0,
                "num_over_T_transforms": 0,
                "by_category": {},
            }

        return {
            "trainer_mode": trainer_mode,
            "architecture_family": request.architecture.family,
            "case_name": prepared.case_name,
            "device": str(device),
            "model_path": str(model_path),
            "best_val_metrics": best_val_metrics,
            "val_metrics": val_metrics,
            "test_metrics": test_metrics,
            "val_violation_report": val_violation_report,
            "test_violation_report": test_violation_report,
            "input_variables": prepared.input_variables,
            "output_variable": prepared.output_variable,
            "lookback": prepared.lookback,
            "out_min": prepared.out_min,
            "out_max": prepared.out_max,
            "rule_summary": rule_summary,
            "rule_specs_count": len(rule_specs),
            "metadata": prepared.metadata,
            "request": request.to_dict(),
        }

    def _evaluateRuleViolations(
            self,
            *,
            model,
            items: list[dict],
            rule_specs: list,
            batch_size: int,
            atol: float,
            rtol: float,
    ) -> dict | None:
        if not rule_specs:
            return None
        relation_tests = [spec.relation_test for spec in rule_specs if getattr(spec, "relation_test", None) is not None]
        if not relation_tests:
            return None
        loader = DataLoader(TimeSeriesDataset(items), batch_size=int(batch_size), shuffle=False)
        return MetamorphicEvaluation.computeViolationReport(
            model=model,
            data_loader=loader,
            metamorphic_tests=relation_tests,
            atol=float(atol),
            rtol=float(rtol),
        )


__all__ = ["TrainingRunner"]
