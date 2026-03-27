from __future__ import annotations

import logging
import time
from collections.abc import Callable
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

logger = logging.getLogger(__name__)


class TrainingRunner:
    def run(
            self,
            request: TrainingRequest,
            output_root: Path,
            epoch_progress_listener: Callable[[int, int], None] | None = None,
    ) -> dict[str, Any]:
        run_start = time.perf_counter()
        logger.info(
            "TrainingRunner started (job_name=%s, family=%s, mode=%s, data_source_kind=%s)",
            request.job_name,
            request.architecture.family,
            request.architecture.mode,
            request.data_source.kind,
        )
        prepared = AdapterFactory.buildPreparedData(request)
        logger.info(
            "Data prepared (job=%s, case=%s, train=%s, val=%s, test=%s, n_features=%s)",
            prepared.job_name,
            prepared.case_name,
            len(prepared.train_items),
            len(prepared.val_items),
            len(prepared.test_items),
            len(prepared.input_variables),
        )
        rule_specs = ApiRuleBuilder.buildRuleSpecsFromApi(
            request.metamorphic.rule_specs,
            feature_names=(prepared.metadata.get("feature_names") or {}),
        )
        logger.info("Metamorphic rule specs built (count=%s)", len(rule_specs))
        device = Device.getDevice()
        logger.info("Using device: %s", str(device))
        output_root.mkdir(parents=True, exist_ok=True)
        model_path = output_root / "model.pt"

        use_metamorphic_mode = request.architecture.mode == "metamorphic" or request.metamorphic.enabled
        can_use_metamorphic = use_metamorphic_mode and len(rule_specs) > 0
        if use_metamorphic_mode and not can_use_metamorphic:
            logger.info("Metamorphic mode requested but no valid rules were provided, using baseline trainer")

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
                    epoch_progress_listener=epoch_progress_listener,
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
                    epoch_progress_listener=epoch_progress_listener,
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
                    epoch_progress_listener=epoch_progress_listener,
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
                    epoch_progress_listener=epoch_progress_listener,
                )
                trainer_mode = "baseline"
        else:
            raise ValueError(f"Unsupported architecture.family: {request.architecture.family}")
        logger.info(
            "Trainer selected (trainer=%s, mode=%s, epochs=%s, batch_size=%s)",
            trainer.__class__.__name__,
            trainer_mode,
            request.architecture.epochs,
            request.architecture.batch_size,
        )

        logger.info("Starting model training")
        training_start = time.perf_counter()
        model, best_val_metrics = trainer.train(prepared.train_items, prepared.val_items)
        training_elapsed_seconds = time.perf_counter() - training_start
        logger.info("Training completed (best_val_mae=%s)", best_val_metrics.get("mae_model"))
        validation_elapsed_seconds: float
        test_elapsed_seconds: float
        if can_use_metamorphic and hasattr(trainer, "evaluate_with_rule_violations"):
            logger.info("Evaluating model with metamorphic violation reports")
            test_start = time.perf_counter()
            test_metrics, test_violation_report = trainer.evaluate_with_rule_violations(
                model=model,
                items=prepared.test_items,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            test_elapsed_seconds = time.perf_counter() - test_start
            validation_start = time.perf_counter()
            val_metrics, val_violation_report = trainer.evaluate_with_rule_violations(
                model=model,
                items=prepared.val_items,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            validation_elapsed_seconds = time.perf_counter() - validation_start
        else:
            logger.info("Evaluating model without integrated metamorphic trainer")
            test_start = time.perf_counter()
            test_metrics = trainer.evaluate(model, prepared.test_items)
            test_violation_report = self._evaluateRuleViolations(
                model=model,
                items=prepared.test_items,
                rule_specs=rule_specs,
                batch_size=request.architecture.batch_size,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            test_elapsed_seconds = time.perf_counter() - test_start
            validation_start = time.perf_counter()
            val_metrics = trainer.evaluate(model, prepared.val_items)
            val_violation_report = self._evaluateRuleViolations(
                model=model,
                items=prepared.val_items,
                rule_specs=rule_specs,
                batch_size=request.architecture.batch_size,
                atol=request.metamorphic.violation_atol,
                rtol=request.metamorphic.violation_rtol,
            )
            validation_elapsed_seconds = time.perf_counter() - validation_start

        torch.save(model.state_dict(), model_path)
        logger.info("Model artifact saved (path=%s)", str(model_path))
        self._logViolationReport(scope="validation", report=val_violation_report)
        self._logViolationReport(scope="test", report=test_violation_report)
        if rule_specs:
            rule_summary = summarize_rule_specs(rule_specs)
        else:
            rule_summary = {
                "num_specs": 0,
                "num_relation_tests": 0,
                "num_over_T_transforms": 0,
                "by_category": {},
            }
        logger.info(
            "TrainingRunner finished (job=%s, case=%s, trainer_mode=%s, test_samples=%s)",
            prepared.job_name,
            prepared.case_name,
            trainer_mode,
            test_metrics.get("n_samples"),
        )
        total_elapsed_seconds = time.perf_counter() - run_start

        return {
            "trainer_mode": trainer_mode,
            "architecture_family": request.architecture.family,
            "job_name": prepared.job_name,
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
            "training_elapsed_seconds": round(training_elapsed_seconds, 6),
            "test_elapsed_seconds": round(test_elapsed_seconds, 6),
            "validation_elapsed_seconds": round(validation_elapsed_seconds, 6),
            "total_elapsed_seconds": round(total_elapsed_seconds, 6),
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

    @staticmethod
    def _logViolationReport(*, scope: str, report: dict | None) -> None:
        if report is None:
            logger.info("%s violation report: not available", scope)
            return
        logger.info(
            "%s violation report summary (overall_rate=%s, total_violations=%s, total_cases=%s)",
            scope,
            report.get("overall_violation_rate"),
            report.get("total_violations"),
            report.get("total_cases"),
        )
        by_test = report.get("by_test")
        if not isinstance(by_test, dict) or not by_test:
            logger.info("%s violation report by rule: empty", scope)
            return
        for rule_name in sorted(by_test.keys()):
            stats = by_test.get(rule_name) or {}
            logger.info(
                "%s violation by rule (rule=%s, violations=%s, total=%s, violation_rate=%s)",
                scope,
                rule_name,
                stats.get("violations"),
                stats.get("total"),
                stats.get("violation_rate"),
            )


__all__ = ["TrainingRunner"]
