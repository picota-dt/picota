from __future__ import annotations

import io
import json
import logging
import sys
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SERVICE_ROOT = TESTS_DIR.parent
REPO_ROOT = SERVICE_ROOT.parent
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from picota.framework.control.TrainingService import TrainingService
from picota.framework.control.ticket.TicketStore import TicketStore
from picota.framework.model.TrainingRequest import TrainingRequest
from tests.use_case_data_sources import energigran_data_source
from tests.use_case_rules import energigran_rule_specs


class EnergigranOperationLoggingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="picota-energigran-operation-")
        self.ticket_dir = Path(self.temp_dir.name)
        self.commander = TrainingService.createCommander(ticket_dir=self.ticket_dir)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_energigran_operation_logs_full_training_flow_without_api(self) -> None:
        payload = {
            "job_name": "energigran-operation-logged",
            "data_source": energigran_data_source(REPO_ROOT),
            "time_horizon": {"value": 24, "unit": "hours"},
            "target_variable": "generation",
            "lookback": 0,
            "architecture": {
                "family": "kan",
                "mode": "metamorphic",
                "epochs": 50,
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
                "enabled": True,
                "supervised_weight": 1.0,
                "relation_constraint_weight": 0.2,
                "worst_case_over_t_weight": 0.0,
                "violation_atol": 1e-6,
                "violation_rtol": 1e-4,
                "rule_specs": energigran_rule_specs(),
            },
        }
        epochs = int(payload["architecture"]["epochs"])
        request = TrainingRequest.from_dict(payload)

        with self._capture_framework_logs() as log_stream:
            created = self.commander.execute(request)
            ticket_id = str(created["ticket_id"])
            ticket = self.commander.wait(ticket_id=ticket_id, timeout_sec=1800.0, poll_sec=0.2)

        logs = log_stream.getvalue()
        ticket_response = self.commander.get_ticket(ticket_id)
        self.assertIsNotNone(ticket_response)
        assert ticket_response is not None

        print("\n=== final ticket response payload (get by ticket_id) ===")
        print(json.dumps(ticket_response, ensure_ascii=True, indent=2, sort_keys=True))

        self.assertIsNotNone(ticket)
        assert ticket is not None
        self.assertEqual(TicketStore.STATUS_COMPLETED, ticket["status"])
        result = ticket_response["result"]
        self.assertEqual(payload["job_name"], result["job_name"])
        self.assertEqual("energigran", result["case_name"])
        self.assertEqual("metamorphic", result["trainer_mode"])
        self.assertGreater(result["test_metrics"]["n_samples"], 0)
        self.assertTrue(Path(result["model_path"]).exists())
        self.assertIsNotNone(result["val_violation_report"])
        self.assertIsNotNone(result["test_violation_report"])
        self.assertIn("total_violations", result["test_violation_report"])
        self.assertIn("by_test", result["test_violation_report"])

        print("\n=== validation violation report ===")
        print(json.dumps(result["val_violation_report"], ensure_ascii=True, indent=2, sort_keys=True))
        print("\n=== test violation report ===")
        print(json.dumps(result["test_violation_report"], ensure_ascii=True, indent=2, sort_keys=True))

        self.assertIn("Ticket created", logs)
        self.assertIn("TrainingRunner started", logs)
        self.assertIn("Data prepared", logs)
        self.assertIn("Metamorphic rule specs built", logs)
        self.assertIn("Trainer selected", logs)
        self.assertIn("train_loss=", logs)
        self.assertIn(f"epoch 1/{epochs} finished", logs)
        self.assertIn(f"epoch {epochs}/{epochs} finished", logs)
        self.assertIn("training completed", logs.lower())
        self.assertIn("Ticket completed", logs)

    @contextmanager
    def _capture_framework_logs(self):
        logger = logging.getLogger("picota.framework")
        previous_level = logger.level
        previous_propagate = logger.propagate
        stream = io.StringIO()
        handler = logging.StreamHandler(stream)
        live_handler = logging.StreamHandler(sys.stdout)
        handler.setLevel(logging.INFO)
        live_handler.setLevel(logging.INFO)
        handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s | %(message)s"))
        live_handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s | %(message)s"))
        logger.setLevel(logging.INFO)
        logger.propagate = False
        logger.addHandler(handler)
        logger.addHandler(live_handler)
        try:
            yield stream
        finally:
            logger.removeHandler(handler)
            logger.removeHandler(live_handler)
            logger.setLevel(previous_level)
            logger.propagate = previous_propagate


if __name__ == "__main__":
    unittest.main()
