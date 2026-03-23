from __future__ import annotations

import json
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SERVICE_ROOT = TESTS_DIR.parent
REPO_ROOT = SERVICE_ROOT.parent
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from picota.framework.control.TrainingService import TrainingService
from picota.framework.control.adapters.AdapterFactory import AdapterFactory
from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.model.TrainingRequest import TrainingRequest
from picota.framework.view.HttpServerFactory import HttpServerFactory
from picota.framework.view.PicotaApiController import PicotaApiController
from tests.use_case_data_sources import energigran_data_source, europlatano_data_source
from tests.use_case_rules import energigran_rule_specs, europlatano_rule_specs


class TrainingFrameworkE2ETest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="picota-training-tests-")
        self.ticket_dir = Path(self.temp_dir.name)
        self.commander = TrainingService.createCommander(ticket_dir=self.ticket_dir)
        self.controller = PicotaApiController(self.commander)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_energigran_case_end_to_end(self) -> None:
        payload = {
            "job_name": "energigran-e2e",
            "data_source": energigran_data_source(REPO_ROOT, limit_rows=400),
            "time_horizon": {"value": 24, "unit": "hours"},
            "target_variable": "generation",
            "lookback": 0,
            "architecture": {
                "family": "kan",
                "mode": "baseline",
                "epochs": 1,
                "batch_size": 64,
                "learning_rate": 5e-4,
                "seed": 7,
            },
            "split": {
                "train_ratio": 0.64,
                "val_ratio": 0.16,
                "test_ratio": 0.20,
            },
            "metamorphic": {
                "rule_specs": energigran_rule_specs(),
            },
        }
        ticket = self._submit_and_wait(payload)
        self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])
        result = ticket["result"]
        self.assertEqual(payload["job_name"], result["job_name"])
        self.assertEqual("energigran", result["case_name"])
        self.assertGreaterEqual(result["rule_summary"]["num_specs"], 1)
        self.assertGreater(result["test_metrics"]["n_samples"], 0)

    def test_europlatano_case_end_to_end(self) -> None:
        payload = {
            "job_name": "europlatano-e2e",
            "data_source": europlatano_data_source(REPO_ROOT, limit_rows=5000),
            "time_horizon": {"value": 28, "unit": "days"},
            "target_variable": "Production",
            "lookback": 0,
            "architecture": {
                "family": "tabnet",
                "mode": "metamorphic",
                "epochs": 1,
                "batch_size": 96,
                "learning_rate": 5e-4,
                "seed": 5,
                "tabnet_n_steps": 4,
                "tabnet_n_d": 24,
                "tabnet_n_a": 24,
                "tabnet_gamma": 1.3,
                "tabnet_dropout": 0.05,
                "tabnet_mask_temperature": 1.0,
            },
            "metamorphic": {
                "enabled": True,
                "supervised_weight": 1.0,
                "relation_constraint_weight": 0.2,
                "worst_case_over_t_weight": 0.0,
                "violation_atol": 1e-6,
                "violation_rtol": 1e-4,
                "rule_specs": europlatano_rule_specs(),
            },
            "split": {
                "train_ratio": 0.64,
                "val_ratio": 0.16,
                "test_ratio": 0.20,
            },
        }
        ticket = self._submit_and_wait(payload)
        self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])
        result = ticket["result"]
        self.assertEqual(payload["job_name"], result["job_name"])
        self.assertEqual("europlatano", result["case_name"])
        self.assertEqual("metamorphic", result["trainer_mode"])
        self.assertEqual("tabnet", result["architecture_family"])
        self.assertGreater(result["rule_summary"]["num_specs"], 0)
        self.assertGreater(result["test_metrics"]["n_samples"], 0)
        self.assertIsNotNone(result["test_violation_report"])

    def test_infer_pretrained_model_from_completed_ticket(self) -> None:
        payload = {
            "job_name": "energigran-eval-source",
            "data_source": energigran_data_source(REPO_ROOT, limit_rows=400),
            "time_horizon": {"value": 24, "unit": "hours"},
            "target_variable": "generation",
            "lookback": 0,
            "architecture": {
                "family": "kan",
                "mode": "metamorphic",
                "epochs": 1,
                "batch_size": 64,
                "learning_rate": 5e-4,
                "seed": 11,
            },
            "split": {
                "train_ratio": 0.64,
                "val_ratio": 0.16,
                "test_ratio": 0.20,
            },
            "metamorphic": {
                "enabled": True,
                "supervised_weight": 1.0,
                "relation_constraint_weight": 0.2,
                "worst_case_over_t_weight": 0.0,
                "violation_atol": 1e-6,
                "violation_rtol": 1e-4,
                "rule_specs": energigran_rule_specs(),
            },
        }
        ticket = self._submit_and_wait(payload)
        self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])
        ticket_id = str(ticket["ticket_id"])
        result = dict(ticket["result"])
        self.assertEqual(payload["job_name"], result["job_name"])
        self.assertEqual("energigran", result["case_name"])
        self.assertGreater(result["test_metrics"]["n_samples"], 0)
        self.assertGreaterEqual(result["rule_summary"]["num_specs"], 1)

        prepared = AdapterFactory.buildPreparedData(TrainingRequest.from_dict(payload))
        sample_item = prepared.test_items[0]
        feature_names = dict(prepared.metadata.get("feature_names") or {})
        variables = self._variables_from_sample(sample_item, feature_names)
        infer_status, inference = self.controller.create_inference(
            {
                "training_ticket_id": ticket_id,
                "output_scale": "both",
                "instances": [{"variables": variables}],
            }
        )
        self.assertEqual(200, int(infer_status))
        self.assertEqual(ticket_id, inference["source_training_ticket_id"])
        self.assertEqual("both", inference["output_scale"])
        self.assertEqual(1, len(inference["predictions"]))
        self.assertIn("normalized", inference["predictions"][0])
        self.assertIn("raw", inference["predictions"][0])

    def test_http_controller_end_to_end(self) -> None:
        server = HttpServerFactory.create(host="127.0.0.1", port=0, controller=self.controller)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            host, port = server.server_address
            base_url = f"http://{host}:{port}"
            payload = {
                "job_name": "energigran-http-e2e",
                "data_source": energigran_data_source(REPO_ROOT, limit_rows=400),
                "time_horizon": {"value": 24, "unit": "hours"},
                "target_variable": "generation",
                "architecture": {
                    "family": "kan",
                    "mode": "baseline",
                    "epochs": 1,
                    "batch_size": 32,
                    "learning_rate": 1e-3,
                    "seed": 17,
                },
                "metamorphic": {
                    "rule_specs": energigran_rule_specs(),
                },
            }
            create_response = self._http_json(
                method="POST",
                url=f"{base_url}/api/v1/trainings",
                body=payload,
            )
            ticket_id = str(create_response["ticket_id"])
            ticket = self._wait_ticket_http(base_url, ticket_id=ticket_id, timeout_sec=180.0)
            self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])
            self.assertGreater(ticket["result"]["test_metrics"]["n_samples"], 0)

            prepared = AdapterFactory.buildPreparedData(TrainingRequest.from_dict(payload))
            sample_item = prepared.test_items[0]
            feature_names = dict(prepared.metadata.get("feature_names") or {})
            variables = self._variables_from_sample(sample_item, feature_names)
            inference_response = self._http_json(
                method="POST",
                url=f"{base_url}/api/v1/inferences",
                body={
                    "training_ticket_id": ticket_id,
                    "output_scale": "raw",
                    "instances": [{"variables": variables}],
                },
            )
            self.assertEqual(ticket_id, inference_response["source_training_ticket_id"])
            self.assertEqual("raw", inference_response["output_scale"])
            self.assertEqual(1, len(inference_response["predictions"]))
            self.assertIn("value", inference_response["predictions"][0])
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5.0)

    def _submit_and_wait(self, payload: dict) -> dict:
        status, body = self.controller.create_training(payload)
        self.assertEqual(202, int(status))
        ticket_id = str(body["ticket_id"])
        deadline = time.monotonic() + 300.0
        while time.monotonic() < deadline:
            current_status, record = self.controller.get_training(ticket_id)
            self.assertEqual(200, int(current_status))
            state = str(record.get("status"))
            if state in {"completed", "failed"}:
                return record
            time.sleep(0.2)
        raise TimeoutError(f"Timeout waiting training ticket {ticket_id}")

    def _wait_ticket_http(self, base_url: str, *, ticket_id: str, timeout_sec: float) -> dict:
        deadline = time.monotonic() + timeout_sec
        while time.monotonic() < deadline:
            ticket = self._http_json(
                method="GET",
                url=f"{base_url}/api/v1/trainings/{ticket_id}",
                body=None,
            )
            if str(ticket.get("status")) in {"completed", "failed"}:
                return ticket
            time.sleep(0.2)
        raise TimeoutError(f"Timeout waiting ticket {ticket_id}")

    @staticmethod
    def _http_json(method: str, url: str, body: dict | None) -> dict:
        payload: bytes | None = None
        headers = {"Content-Type": "application/json"}
        if body is not None:
            payload = json.dumps(body).encode("utf-8")
        request = urllib.request.Request(url=url, method=method, data=payload, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))

    @staticmethod
    def _variables_from_sample(sample_item: dict, feature_names: dict[str, list[str]]) -> dict[str, float]:
        variables: dict[str, float] = {}
        for key in (
                "t",
                "numerical_t_features",
                "categorical_t_features",
                "lookback_t",
                "numerical_lookback_features",
                "categorical_lookback_features",
        ):
            names = list(feature_names.get(key) or [])
            values = list(sample_item.get(key) or [])
            if len(names) != len(values):
                raise ValueError(
                    f"Sample shape mismatch for {key}: expected {len(names)} values, got {len(values)}"
                )
            for index, name in enumerate(names):
                variables[str(name)] = float(values[index])
        return variables


class CaseWorkspaceApiTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="picota-case-workspace-")
        self.workspace_dir = Path(self.temp_dir.name)
        self.controller = TrainingService.createController(workspace_dir=self.workspace_dir)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_case_dataset_training_persists_workspace_layout(self) -> None:
        case_id = "energigran_case"
        status_case, created_case = self.controller.create_case({"case_id": case_id})
        self.assertEqual(201, int(status_case))
        self.assertEqual(case_id, created_case["case_id"])

        source_path = REPO_ROOT / "runtime.test" / "data" / "energigran.tsv"
        status_dataset, created_dataset = self.controller.create_case_dataset(
            case_id,
            {
                "dataset_id": "energigran_source",
                "source_path": str(source_path),
            },
        )
        self.assertEqual(201, int(status_dataset))
        self.assertEqual("energigran_source", created_dataset["dataset_id"])
        self.assertTrue(Path(created_dataset["stored_path"]).exists())

        status_list, datasets_body = self.controller.list_case_datasets(case_id)
        self.assertEqual(200, int(status_list))
        self.assertEqual(1, len(datasets_body["datasets"]))
        self.assertEqual("energigran_source", datasets_body["datasets"][0]["dataset_id"])

        payload = {
            "job_name": "energigran-case-training",
            "data_source": {
                "kind": "tabular_timeseries",
                "dataset_id": "energigran_source",
                "limit_rows": 300,
                "options": {
                    "case_name": "energigran",
                    "timestamp_column": "instant",
                    "target_column": "generation",
                    "time_bucket": "hour",
                    "entity_key_columns": [],
                    "numerical_input_columns": [
                        "cellTemperature",
                        "Infecar.temperature",
                        "Infecar.radiation",
                        "grid",
                        "consumption",
                    ],
                    "categorical_input_columns": [],
                    "numerical_scaler": "zscore",
                    "categorical_encoding": "none",
                },
            },
            "time_horizon": {"value": 24, "unit": "hours"},
            "target_variable": "generation",
            "lookback": 0,
            "architecture": {
                "family": "kan",
                "mode": "baseline",
                "epochs": 1,
                "batch_size": 64,
                "learning_rate": 5e-4,
                "seed": 7,
            },
            "split": {
                "train_ratio": 0.64,
                "val_ratio": 0.16,
                "test_ratio": 0.20,
            },
            "metamorphic": {
                "rule_specs": energigran_rule_specs(),
            },
        }
        status_training, accepted = self.controller.create_case_training(case_id, payload)
        self.assertEqual(202, int(status_training))
        ticket_id = str(accepted["ticket_id"])
        ticket = self._wait_ticket(ticket_id)
        self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])

        case_dir = self.workspace_dir / case_id
        ticket_dir = case_dir / "tickets" / ticket_id
        request_json = ticket_dir / "request.json"
        result_json = ticket_dir / "result.json"
        model_path = Path(str(ticket["result"]["model_path"]))

        self.assertTrue((case_dir / "datasets").exists())
        self.assertTrue(request_json.exists())
        self.assertTrue(result_json.exists())
        self.assertTrue(model_path.exists())

        with request_json.open("r", encoding="utf-8") as handle:
            persisted_request = json.load(handle)
        self.assertEqual("energigran_source", persisted_request["data_source"]["dataset_id"])
        self.assertTrue(str(persisted_request["data_source"]["path"]).endswith(".tsv"))

        status_delete, deleted_case = self.controller.delete_case(case_id)
        self.assertEqual(200, int(status_delete))
        self.assertTrue(bool(deleted_case["deleted"]))
        self.assertFalse(case_dir.exists())

    def test_http_case_and_dataset_endpoints(self) -> None:
        server = HttpServerFactory.create(host="127.0.0.1", port=0, controller=self.controller)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            host, port = server.server_address
            base_url = f"http://{host}:{port}"
            case_id = "http_case"
            create_case = self._http_json(
                method="POST",
                url=f"{base_url}/api/v1/cases",
                body={"case_id": case_id},
            )
            self.assertEqual(case_id, create_case["case_id"])

            source_path = REPO_ROOT / "runtime.test" / "data" / "energigran.tsv"
            create_dataset = self._http_json(
                method="POST",
                url=f"{base_url}/api/v1/cases/{case_id}/datasets",
                body={"dataset_id": "http_ds", "source_path": str(source_path)},
            )
            self.assertEqual("http_ds", create_dataset["dataset_id"])

            dataset_list = self._http_json(
                method="GET",
                url=f"{base_url}/api/v1/cases/{case_id}/datasets",
                body=None,
            )
            self.assertEqual(case_id, dataset_list["case_id"])
            self.assertEqual(1, len(dataset_list["datasets"]))

            deleted = self._http_json(
                method="DELETE",
                url=f"{base_url}/api/v1/cases/{case_id}",
                body=None,
            )
            self.assertTrue(bool(deleted["deleted"]))
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5.0)

    def _wait_ticket(self, ticket_id: str) -> dict:
        deadline = time.monotonic() + 300.0
        while time.monotonic() < deadline:
            status, record = self.controller.get_training(ticket_id)
            self.assertEqual(200, int(status))
            if str(record.get("status")) in {"completed", "failed"}:
                return record
            time.sleep(0.2)
        raise TimeoutError(f"Timeout waiting ticket {ticket_id}")

    @staticmethod
    def _http_json(method: str, url: str, body: dict | None) -> dict:
        payload: bytes | None = None
        headers = {"Content-Type": "application/json"}
        if body is not None:
            payload = json.dumps(body).encode("utf-8")
        request = urllib.request.Request(url=url, method=method, data=payload, headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
