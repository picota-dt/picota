from __future__ import annotations

from pathlib import Path

from picota.framework.control.TrainingCommander import TrainingCommander
from picota.framework.model.TrainingCommandConfig import TrainingCommandConfig
from picota.framework.view.HttpServerFactory import HttpServerFactory
from picota.framework.view.TrainingApiController import TrainingApiController


class TrainingService:
    REPO_ROOT = Path(__file__).resolve().parents[3]
    DEFAULT_TICKETS_DIR = REPO_ROOT / "temp" / "training-tickets"

    @staticmethod
    def createCommander(ticket_dir: Path | None = None) -> TrainingCommander:
        config = TrainingCommandConfig(ticket_dir=(ticket_dir or TrainingService.DEFAULT_TICKETS_DIR).resolve())
        return TrainingCommander(config=config)

    @staticmethod
    def createController(ticket_dir: Path | None = None) -> TrainingApiController:
        return TrainingApiController(TrainingService.createCommander(ticket_dir=ticket_dir))

    @staticmethod
    def runHttpService(host: str = "0.0.0.0", port: int = 8080, ticket_dir: Path | None = None) -> None:
        controller = TrainingService.createController(ticket_dir=ticket_dir)
        server = HttpServerFactory.create(host=host, port=port, controller=controller)
        server.serve_forever()
