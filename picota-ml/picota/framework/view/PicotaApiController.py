from __future__ import annotations

from http import HTTPStatus
from pathlib import Path
from typing import Any

from picota.framework.control.CaseWorkspaceManager import CaseWorkspaceManager
from picota.framework.control.ModelInferenceCommander import ModelInferenceCommander
from picota.framework.control.ModelInferenceRunner import ModelInferenceRunner
from picota.framework.control.TrainingCommander import TrainingCommander
from picota.framework.control.adapters.tabularTimeseries.RequestOptionsParser import RequestOptionsParser
from picota.framework.model.InferenceRequest import InferenceRequest
from picota.framework.model.TrainingCommandConfig import TrainingCommandConfig
from picota.framework.model.TrainingConfigError import TrainingConfigError
from picota.framework.model.TrainingRequest import TrainingRequest


class PicotaApiController:
    def __init__(
            self,
            commander: TrainingCommander,
            inference_commander: ModelInferenceCommander | None = None,
            workspace_manager: CaseWorkspaceManager | None = None,
    ):
        self.commander = commander
        self.inference_commander = inference_commander or ModelInferenceCommander(ticket_store=commander.ticket_store)
        self.workspace_manager = workspace_manager
        self.inference_runner = ModelInferenceRunner()

    def create_training(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        try:
            request = TrainingRequest.from_dict(payload)
            RequestOptionsParser().parse(request)
            created = self.commander.execute(request)
        except TrainingConfigError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        return HTTPStatus.ACCEPTED, {
            "ticket_id": created["ticket_id"],
            "status": created["status"],
            "created_at": created["created_at"],
            "links": {"self": f"/api/v1/trainings/{created['ticket_id']}"},
        }

    def get_training(self, ticket_id: str) -> tuple[int, dict[str, Any]]:
        if self.workspace_manager is not None:
            found = self.workspace_manager.findTicket(ticket_id)
            if found is not None:
                _case_id, record = found
                return HTTPStatus.OK, record
        record = self.commander.get_ticket(ticket_id)
        if record is None:
            return HTTPStatus.NOT_FOUND, {"error": "ticket_not_found", "ticket_id": ticket_id}
        return HTTPStatus.OK, record

    def create_inference(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        try:
            request = InferenceRequest.from_dict(payload)
            if self.workspace_manager is not None:
                found = self.workspace_manager.findTicket(request.training_ticket_id)
                if found is None:
                    return HTTPStatus.NOT_FOUND, {
                        "error": "training_ticket_not_found",
                        "training_ticket_id": request.training_ticket_id,
                    }
                _case_id, record = found
                status = str(record.get("status"))
                if status != "completed":
                    return HTTPStatus.CONFLICT, {
                        "error": "invalid_training_state",
                        "message": (
                            f"Training ticket '{request.training_ticket_id}' must be completed "
                            f"to infer; current status: {status}"
                        ),
                    }
                result = self.inference_runner.run(training_record=record, request=request)
            else:
                result = self.inference_commander.execute(request)
        except TrainingConfigError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError:
            return HTTPStatus.NOT_FOUND, {
                "error": "training_ticket_not_found",
                "training_ticket_id": str(payload.get("training_ticket_id", "")),
            }
        except ValueError as exc:
            message = str(exc)
            if "must be completed" in message:
                return HTTPStatus.CONFLICT, {"error": "invalid_training_state", "message": message}
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": message}
        return HTTPStatus.OK, result

    def create_case(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        workspace = self._workspaceOrError()
        if workspace is None:
            return HTTPStatus.BAD_REQUEST, {"error": "workspace_not_configured"}
        case_id = str(payload.get("case_id") or "").strip()
        if not case_id:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": "case_id is required"}
        try:
            created = workspace.createCase(case_id)
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileExistsError as exc:
            return HTTPStatus.CONFLICT, {"error": "case_already_exists", "message": str(exc), "case_id": case_id}
        return HTTPStatus.CREATED, created

    def delete_case(self, case_id: str) -> tuple[int, dict[str, Any]]:
        workspace = self._workspaceOrError()
        if workspace is None:
            return HTTPStatus.BAD_REQUEST, {"error": "workspace_not_configured"}
        try:
            workspace.deleteCase(case_id)
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError:
            return HTTPStatus.NOT_FOUND, {"error": "case_not_found", "case_id": case_id}
        return HTTPStatus.OK, {"case_id": case_id, "deleted": True}

    def create_case_dataset(self, case_id: str, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        workspace = self._workspaceOrError()
        if workspace is None:
            return HTTPStatus.BAD_REQUEST, {"error": "workspace_not_configured"}
        source_path = str(payload.get("source_path") or "").strip()
        dataset_id = payload.get("dataset_id")
        if dataset_id is not None:
            dataset_id = str(dataset_id).strip()
        if not source_path:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": "source_path is required"}
        try:
            created = workspace.addDataset(
                case_id=case_id,
                source_path=Path(source_path),
                dataset_id=dataset_id,
            )
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError as exc:
            message = str(exc)
            if "Case not found" in message:
                return HTTPStatus.NOT_FOUND, {"error": "case_not_found", "case_id": case_id}
            return HTTPStatus.NOT_FOUND, {"error": "dataset_source_not_found", "message": message}
        except FileExistsError as exc:
            return HTTPStatus.CONFLICT, {"error": "dataset_already_exists", "message": str(exc)}
        return HTTPStatus.CREATED, created

    def list_case_datasets(self, case_id: str) -> tuple[int, dict[str, Any]]:
        workspace = self._workspaceOrError()
        if workspace is None:
            return HTTPStatus.BAD_REQUEST, {"error": "workspace_not_configured"}
        try:
            datasets = workspace.listDatasets(case_id)
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError:
            return HTTPStatus.NOT_FOUND, {"error": "case_not_found", "case_id": case_id}
        return HTTPStatus.OK, {"case_id": case_id, "datasets": datasets}

    def create_case_training(self, case_id: str, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        workspace = self._workspaceOrError()
        if workspace is None:
            return HTTPStatus.BAD_REQUEST, {"error": "workspace_not_configured"}
        try:
            workspace.ensureCaseExists(case_id)
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError:
            return HTTPStatus.NOT_FOUND, {"error": "case_not_found", "case_id": case_id}

        if not isinstance(payload, dict):
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": "payload must be an object"}
        mutable_payload = dict(payload)
        data_source = mutable_payload.get("data_source")
        if not isinstance(data_source, dict):
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": "data_source is required"}
        dataset_id = str(data_source.get("dataset_id") or "").strip()
        if not dataset_id:
            return HTTPStatus.BAD_REQUEST, {
                "error": "invalid_request",
                "message": "data_source.dataset_id is required",
            }
        try:
            dataset_path = workspace.resolveDatasetPath(case_id=case_id, dataset_id=dataset_id)
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except FileNotFoundError as exc:
            return HTTPStatus.NOT_FOUND, {"error": "dataset_not_found", "message": str(exc)}

        mutable_data_source = dict(data_source)
        mutable_data_source["path"] = str(dataset_path)
        mutable_data_source["dataset_id"] = dataset_id
        mutable_payload["data_source"] = mutable_data_source
        mutable_payload["case_id"] = case_id

        try:
            request = TrainingRequest.from_dict(mutable_payload)
            RequestOptionsParser().parse(request)
            commander = self._commanderForCase(case_id)
            created = commander.execute(request, request_payload=mutable_payload)
        except TrainingConfigError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}
        except ValueError as exc:
            return HTTPStatus.BAD_REQUEST, {"error": "invalid_request", "message": str(exc)}

        return HTTPStatus.ACCEPTED, {
            "ticket_id": created["ticket_id"],
            "status": created["status"],
            "created_at": created["created_at"],
            "case_id": case_id,
            "links": {"self": f"/api/v1/trainings/{created['ticket_id']}"},
        }

    def _workspaceOrError(self) -> CaseWorkspaceManager | None:
        return self.workspace_manager

    def _commanderForCase(self, case_id: str) -> TrainingCommander:
        if self.workspace_manager is None:
            raise RuntimeError("Workspace manager is not configured")
        ticket_store = self.workspace_manager.ticketStore(case_id)
        config = TrainingCommandConfig(ticket_dir=ticket_store.root_dir)
        return TrainingCommander(config=config, ticket_store=ticket_store)


__all__ = ["PicotaApiController"]
