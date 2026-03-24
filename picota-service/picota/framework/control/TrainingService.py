from __future__ import annotations

import logging
from pathlib import Path

from picota.framework.control.CaseWorkspaceManager import CaseWorkspaceManager
from picota.framework.control.TrainingCommander import TrainingCommander
from picota.framework.model.TrainingCommandConfig import TrainingCommandConfig
from picota.framework.view.HttpServerFactory import HttpServerFactory
from picota.framework.view.PicotaApiController import PicotaApiController

logger = logging.getLogger(__name__)


class TrainingService:
    REPO_ROOT = Path(__file__).resolve().parents[3]
    DEFAULT_WORKSPACE_DIR = REPO_ROOT / "temp" / "training-workspace"
    DEFAULT_CASE_ID = "default"

    @staticmethod
    def createWorkspaceManager(
            *,
            workspace_dir: Path | None = None,
            ticket_dir: Path | None = None,
    ) -> CaseWorkspaceManager:
        resolved_workspace = TrainingService._resolveWorkspaceDir(
            workspace_dir=workspace_dir,
            ticket_dir=ticket_dir,
        )
        return CaseWorkspaceManager(resolved_workspace)

    @staticmethod
    def createCommander(
            *,
            case_id: str | None = None,
            workspace_dir: Path | None = None,
            ticket_dir: Path | None = None,
    ) -> TrainingCommander:
        target_case_id = str(case_id or TrainingService.DEFAULT_CASE_ID)
        workspace = TrainingService.createWorkspaceManager(
            workspace_dir=workspace_dir,
            ticket_dir=ticket_dir,
        )
        try:
            workspace.ensureCaseExists(target_case_id)
        except FileNotFoundError:
            workspace.createCase(target_case_id)
        ticket_store = workspace.ticketStore(target_case_id)
        config = TrainingCommandConfig(ticket_dir=ticket_store.root_dir)
        return TrainingCommander(config=config, ticket_store=ticket_store)

    @staticmethod
    def createController(
            *,
            workspace_dir: Path | None = None,
            ticket_dir: Path | None = None,
    ) -> PicotaApiController:
        workspace = TrainingService.createWorkspaceManager(
            workspace_dir=workspace_dir,
            ticket_dir=ticket_dir,
        )
        try:
            workspace.ensureCaseExists(TrainingService.DEFAULT_CASE_ID)
        except FileNotFoundError:
            workspace.createCase(TrainingService.DEFAULT_CASE_ID)
        commander = TrainingService.createCommander(
            case_id=TrainingService.DEFAULT_CASE_ID,
            workspace_dir=workspace.workspace_dir,
        )
        return PicotaApiController(commander, workspace_manager=workspace)

    @staticmethod
    def runHttpService(
            host: str = "0.0.0.0",
            port: int = 8080,
            workspace_dir: Path | None = None,
            ticket_dir: Path | None = None,
            log_api_requests: bool = False,
    ) -> None:
        controller = TrainingService.createController(
            workspace_dir=workspace_dir,
            ticket_dir=ticket_dir,
        )
        server = HttpServerFactory.create(
            host=host,
            port=port,
            controller=controller,
            log_api_requests=log_api_requests,
        )
        logger.info(
            "Picota training service started (host=%s, port=%s, log_api_requests=%s)",
            host,
            int(port),
            bool(log_api_requests),
        )
        server.serve_forever()

    @staticmethod
    def _resolveWorkspaceDir(
            *,
            workspace_dir: Path | None,
            ticket_dir: Path | None,
    ) -> Path:
        legacy_dir = ticket_dir
        selected = workspace_dir or legacy_dir or TrainingService.DEFAULT_WORKSPACE_DIR
        return selected.expanduser().resolve()
