from __future__ import annotations

import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import torch

from picota.framework.control.Device import Device
from picota.framework.control.adapters.AdapterFactory import AdapterFactory
from picota.framework.control.kan.KAN import KAN
from picota.framework.control.rules.ApiRuleBuilder import ApiRuleBuilder
from picota.framework.control.training.TabNetRegressor import TabNetRegressor
from picota.framework.model.InferenceRequest import InferenceRequest
from picota.framework.model.TrainingRequest import TrainingRequest
from picota.framework.model.data.PreparedTrainingData import PreparedTrainingData

logger = logging.getLogger(__name__)


class ModelInferenceRunner:
    _INPUT_KEYS = (
        "t",
        "numerical_t_features",
        "categorical_t_features",
        "lookback_t",
        "numerical_lookback_features",
        "categorical_lookback_features",
    )

    def run(
            self,
            *,
            training_record: dict[str, Any],
            request: InferenceRequest,
    ) -> dict[str, Any]:
        training_request_payload = training_record.get("request")
        if not isinstance(training_request_payload, dict):
            raise ValueError("Training ticket does not contain a valid request payload")
        training_result_payload = training_record.get("result")
        if not isinstance(training_result_payload, dict):
            raise ValueError("Training ticket does not contain a valid training result payload")

        model_path = Path(str(training_result_payload.get("model_path") or "")).expanduser().resolve()
        if not model_path.exists():
            raise FileNotFoundError(f"Trained model artifact not found: {model_path}")

        training_request = TrainingRequest.from_dict(training_request_payload)
        prepared = AdapterFactory.buildPreparedData(training_request)
        rule_specs = ApiRuleBuilder.buildRuleSpecsFromApi(
            training_request.metamorphic.rule_specs,
            feature_names=(prepared.metadata.get("feature_names") or {}),
        )
        device = Device.getDevice()
        model = self._buildModel(
            training_request=training_request,
            prepared=prepared,
            device=device,
        )
        trainer_mode = self._resolveTrainerMode(training_request=training_request, rule_specs_count=len(rule_specs))

        state_dict = torch.load(model_path, map_location=device)
        model.load_state_dict(state_dict)
        model.to(device)
        model.eval()

        encoded_instances = [
            self._encodeInstance(instance=instance, prepared=prepared)
            for instance in request.instances
        ]
        batch = self._buildBatch(encoded_instances, device=device)
        with torch.no_grad():
            prediction_tensor = model(batch).squeeze()
        if prediction_tensor.dim() == 0:
            prediction_tensor = prediction_tensor.unsqueeze(0)

        normalized_predictions = [float(value) for value in prediction_tensor.detach().cpu().tolist()]
        raw_predictions = self._denormalizePredictions(
            normalized_predictions=normalized_predictions,
            out_min=prepared.out_min,
            out_max=prepared.out_max,
        )
        predictions = self._buildPredictions(
            normalized_predictions=normalized_predictions,
            raw_predictions=raw_predictions,
            output_scale=request.output_scale,
        )

        logger.info(
            "Inference completed (ticket_id=%s, instances=%s, output_scale=%s)",
            request.training_ticket_id,
            len(request.instances),
            request.output_scale,
        )
        return {
            "source_training_ticket_id": request.training_ticket_id,
            "job_name": prepared.job_name,
            "case_name": prepared.case_name,
            "architecture_family": training_request.architecture.family,
            "trainer_mode": trainer_mode,
            "device": str(device),
            "model_path": str(model_path),
            "input_variables": list(prepared.input_variables),
            "output_variable": prepared.output_variable,
            "output_scale": request.output_scale,
            "predictions": predictions,
            "inferred_at": datetime.now(timezone.utc).isoformat(),
            "request": request.to_dict(),
        }

    @staticmethod
    def _buildModel(
            *,
            training_request: TrainingRequest,
            prepared: PreparedTrainingData,
            device,
    ) -> torch.nn.Module:
        if training_request.architecture.family == "kan":
            return KAN(
                input_features=len(prepared.input_variables),
                lookback_size=prepared.lookback,
                means=prepared.means,
                stds=prepared.stds,
                output_features=1,
            ).to(device)
        if training_request.architecture.family == "tabnet":
            return TabNetRegressor(
                input_dim=len(prepared.input_variables),
                output_dim=1,
                n_d=training_request.architecture.tabnet_n_d,
                n_a=training_request.architecture.tabnet_n_a,
                n_steps=training_request.architecture.tabnet_n_steps,
                gamma=training_request.architecture.tabnet_gamma,
                dropout=training_request.architecture.tabnet_dropout,
                mask_temperature=training_request.architecture.tabnet_mask_temperature,
            ).to(device)
        raise ValueError(f"Unsupported architecture.family: {training_request.architecture.family}")

    @staticmethod
    def _resolveTrainerMode(*, training_request: TrainingRequest, rule_specs_count: int) -> str:
        use_metamorphic_mode = (
                training_request.architecture.mode == "metamorphic"
                or training_request.metamorphic.enabled
        )
        can_use_metamorphic = use_metamorphic_mode and int(rule_specs_count) > 0
        return "metamorphic" if can_use_metamorphic else "baseline"

    def _encodeInstance(
            self,
            *,
            instance: dict[str, Any],
            prepared: PreparedTrainingData,
    ) -> dict[str, list[float]]:
        if "variables" in instance:
            return self._encodeInstanceFromVariables(
                variables=instance.get("variables"),
                prepared=prepared,
            )
        return self._encodeInstanceFromVectors(instance=instance, prepared=prepared)

    def _encodeInstanceFromVariables(
            self,
            *,
            variables: Any,
            prepared: PreparedTrainingData,
    ) -> dict[str, list[float]]:
        if not isinstance(variables, dict):
            raise ValueError("instance.variables must be an object")
        feature_names = self._featureNames(prepared)
        encoded: dict[str, list[float]] = {}
        for key in self._INPUT_KEYS:
            required_features = list(feature_names.get(key) or [])
            values: list[float] = []
            for feature_name in required_features:
                if feature_name not in variables:
                    raise ValueError(f"Missing input variable '{feature_name}'")
                values.append(float(variables.get(feature_name)))
            encoded[key] = values
        return encoded

    def _encodeInstanceFromVectors(
            self,
            *,
            instance: dict[str, Any],
            prepared: PreparedTrainingData,
    ) -> dict[str, list[float]]:
        encoded: dict[str, list[float]] = {}
        for key in self._INPUT_KEYS:
            value = instance.get(key, [])
            if value is None:
                value = []
            if not isinstance(value, list):
                raise ValueError(f"instance.{key} must be a list")
            encoded[key] = [float(item) for item in value]
        self._validateVectorLengths(encoded=encoded, prepared=prepared)
        return encoded

    def _validateVectorLengths(self, *, encoded: dict[str, list[float]], prepared: PreparedTrainingData) -> None:
        feature_names = self._featureNames(prepared)
        for key in self._INPUT_KEYS:
            expected_size = len(feature_names.get(key) or [])
            current_size = len(encoded.get(key) or [])
            if expected_size != current_size:
                raise ValueError(
                    f"instance.{key} size mismatch (expected={expected_size}, got={current_size})"
                )

    @staticmethod
    def _featureNames(prepared: PreparedTrainingData) -> dict[str, list[str]]:
        raw_feature_names = prepared.metadata.get("feature_names") or {}
        if not isinstance(raw_feature_names, dict):
            return {}
        return {
            "t": list(raw_feature_names.get("t") or []),
            "numerical_t_features": list(raw_feature_names.get("numerical_t_features") or []),
            "categorical_t_features": list(raw_feature_names.get("categorical_t_features") or []),
            "lookback_t": list(raw_feature_names.get("lookback_t") or []),
            "numerical_lookback_features": list(raw_feature_names.get("numerical_lookback_features") or []),
            "categorical_lookback_features": list(raw_feature_names.get("categorical_lookback_features") or []),
        }

    def _buildBatch(self, encoded_instances: list[dict[str, list[float]]], *, device) -> dict[str, torch.Tensor]:
        if len(encoded_instances) == 0:
            raise ValueError("instances must be non-empty")
        batch: dict[str, torch.Tensor] = {
            "out": torch.zeros((len(encoded_instances),), dtype=torch.float32, device=device),
        }
        for key in self._INPUT_KEYS:
            values = [instance[key] for instance in encoded_instances]
            batch[key] = torch.tensor(values, dtype=torch.float32, device=device)
        return batch

    @staticmethod
    def _denormalizePredictions(
            *,
            normalized_predictions: list[float],
            out_min: float,
            out_max: float,
    ) -> list[float]:
        span = float(out_max - out_min)
        if span <= 0.0:
            return [float(out_min) for _ in normalized_predictions]
        return [float(out_min + (prediction * span)) for prediction in normalized_predictions]

    @staticmethod
    def _buildPredictions(
            *,
            normalized_predictions: list[float],
            raw_predictions: list[float],
            output_scale: str,
    ) -> list[dict[str, Any]]:
        payload: list[dict[str, Any]] = []
        for index, (normalized_value, raw_value) in enumerate(zip(normalized_predictions, raw_predictions)):
            if output_scale == "normalized":
                payload.append({"index": index, "value": normalized_value})
            elif output_scale == "raw":
                payload.append({"index": index, "value": raw_value})
            else:
                payload.append(
                    {
                        "index": index,
                        "normalized": normalized_value,
                        "raw": raw_value,
                    }
                )
        return payload


__all__ = ["ModelInferenceRunner"]
