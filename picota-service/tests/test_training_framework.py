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
from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.view.HttpServerFactory import HttpServerFactory
from picota.framework.view.TrainingApiController import TrainingApiController
from tests.use_case_data_sources import energigran_data_source, europlatano_data_source
from tests.use_case_rules import energigran_rule_specs, europlatano_rule_specs


class TrainingFrameworkE2ETest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="picota-training-tests-")
        self.ticket_dir = Path(self.temp_dir.name)
        self.commander = TrainingService.createCommander(ticket_dir=self.ticket_dir)
        self.controller = TrainingApiController(self.commander)

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
        self.assertEqual("europlatano", result["case_name"])
        self.assertEqual("metamorphic", result["trainer_mode"])
        self.assertEqual("tabnet", result["architecture_family"])
        self.assertGreater(result["rule_summary"]["num_specs"], 0)
        self.assertGreater(result["test_metrics"]["n_samples"], 0)
        self.assertIsNotNone(result["test_violation_report"])

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


if __name__ == "__main__":
    unittest.main()
