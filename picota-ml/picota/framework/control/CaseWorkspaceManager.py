from __future__ import annotations

import json
import re
import shutil
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from picota.framework.control.ticket.TicketStore import TicketStore


class CaseWorkspaceManager:
    _ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$")

    def __init__(self, workspace_dir: Path):
        self.workspace_dir = workspace_dir.expanduser().resolve()
        self.workspace_dir.mkdir(parents=True, exist_ok=True)

    def createCase(self, case_id: str) -> dict[str, Any]:
        normalized = self._normalizeId(case_id, field_name="case_id")
        case_dir = self.caseDir(normalized)
        if case_dir.exists():
            raise FileExistsError(f"Case already exists: {normalized}")
        self.datasetsDir(normalized).mkdir(parents=True, exist_ok=False)
        self.ticketsDir(normalized).mkdir(parents=True, exist_ok=False)
        return {
            "case_id": normalized,
            "created_at": self._utcNowIso(),
            "paths": {
                "case_dir": str(case_dir),
                "datasets_dir": str(self.datasetsDir(normalized)),
                "tickets_dir": str(self.ticketsDir(normalized)),
            },
        }

    def deleteCase(self, case_id: str) -> None:
        normalized = self._normalizeId(case_id, field_name="case_id")
        case_dir = self.caseDir(normalized)
        if not case_dir.exists():
            raise FileNotFoundError(f"Case not found: {normalized}")
        shutil.rmtree(case_dir)

    def addDataset(
            self,
            *,
            case_id: str,
            source_path: Path,
            dataset_id: str | None = None,
    ) -> dict[str, Any]:
        normalized_case_id = self._normalizeId(case_id, field_name="case_id")
        self.ensureCaseExists(normalized_case_id)
        source = source_path.expanduser().resolve()
        if not source.exists() or not source.is_file():
            raise FileNotFoundError(f"Dataset source file not found: {source}")

        normalized_dataset_id = self._normalizeId(
            dataset_id or uuid.uuid4().hex,
            field_name="dataset_id",
        )
        dataset_dir = self.datasetDir(normalized_case_id, normalized_dataset_id)
        if dataset_dir.exists():
            raise FileExistsError(
                f"Dataset '{normalized_dataset_id}' already exists in case '{normalized_case_id}'"
            )
        dataset_dir.mkdir(parents=True, exist_ok=False)

        target_name = f"data{source.suffix}" if source.suffix else "data"
        target_path = dataset_dir / target_name
        shutil.copy2(source, target_path)

        metadata = {
            "dataset_id": normalized_dataset_id,
            "case_id": normalized_case_id,
            "created_at": self._utcNowIso(),
            "source_path": str(source),
            "stored_path": str(target_path),
            "original_filename": source.name,
            "stored_filename": target_name,
            "size_bytes": int(target_path.stat().st_size),
        }
        self._writeJson(dataset_dir / "metadata.json", metadata)
        return metadata

    def listDatasets(self, case_id: str) -> list[dict[str, Any]]:
        normalized_case_id = self._normalizeId(case_id, field_name="case_id")
        self.ensureCaseExists(normalized_case_id)
        datasets_root = self.datasetsDir(normalized_case_id)
        result: list[dict[str, Any]] = []
        for candidate in sorted(datasets_root.iterdir(), key=lambda path: path.name):
            metadata_path = candidate / "metadata.json"
            if not candidate.is_dir() or not metadata_path.exists():
                continue
            with metadata_path.open("r", encoding="utf-8") as handle:
                payload = json.load(handle)
            if isinstance(payload, dict):
                result.append(payload)
        return result

    def resolveDatasetPath(self, *, case_id: str, dataset_id: str) -> Path:
        normalized_case_id = self._normalizeId(case_id, field_name="case_id")
        normalized_dataset_id = self._normalizeId(dataset_id, field_name="dataset_id")
        metadata_path = self.datasetDir(normalized_case_id, normalized_dataset_id) / "metadata.json"
        if not metadata_path.exists():
            raise FileNotFoundError(
                f"Dataset '{normalized_dataset_id}' not found in case '{normalized_case_id}'"
            )
        with metadata_path.open("r", encoding="utf-8") as handle:
            metadata = json.load(handle)
        stored_path = Path(str((metadata or {}).get("stored_path", ""))).expanduser().resolve()
        if not stored_path.exists():
            raise FileNotFoundError(f"Stored dataset file not found: {stored_path}")
        return stored_path

    def ticketStore(self, case_id: str) -> TicketStore:
        normalized = self._normalizeId(case_id, field_name="case_id")
        self.ensureCaseExists(normalized)
        return TicketStore(self.ticketsDir(normalized))

    def findTicket(self, ticket_id: str) -> tuple[str, dict[str, Any]] | None:
        normalized_ticket_id = str(ticket_id).strip()
        if not normalized_ticket_id:
            return None
        for case_dir in sorted(self.workspace_dir.iterdir(), key=lambda path: path.name):
            if not case_dir.is_dir():
                continue
            case_id = case_dir.name
            tickets_dir = case_dir / "tickets"
            if not tickets_dir.exists():
                continue
            result_path = tickets_dir / normalized_ticket_id / TicketStore.RESULT_FILE_NAME
            if not result_path.exists():
                continue
            record = TicketStore(tickets_dir).get(normalized_ticket_id)
            if record is None:
                continue
            enriched = dict(record)
            enriched["case_id"] = case_id
            return case_id, enriched
        return None

    def ensureCaseExists(self, case_id: str) -> None:
        normalized = self._normalizeId(case_id, field_name="case_id")
        case_dir = self.caseDir(normalized)
        if not case_dir.exists() or not case_dir.is_dir():
            raise FileNotFoundError(f"Case not found: {normalized}")
        self.datasetsDir(normalized).mkdir(parents=True, exist_ok=True)
        self.ticketsDir(normalized).mkdir(parents=True, exist_ok=True)

    def caseDir(self, case_id: str) -> Path:
        return self.workspace_dir / case_id

    def datasetsDir(self, case_id: str) -> Path:
        return self.caseDir(case_id) / "datasets"

    def ticketsDir(self, case_id: str) -> Path:
        return self.caseDir(case_id) / "tickets"

    def datasetDir(self, case_id: str, dataset_id: str) -> Path:
        return self.datasetsDir(case_id) / dataset_id

    @classmethod
    def _normalizeId(cls, value: str | None, *, field_name: str) -> str:
        normalized = str(value or "").strip()
        if not normalized:
            raise ValueError(f"{field_name} must not be empty")
        if cls._ID_PATTERN.match(normalized) is None:
            raise ValueError(
                f"{field_name} '{normalized}' contains unsupported characters "
                "(allowed: letters, digits, '_', '-', '.')"
            )
        return normalized

    @staticmethod
    def _utcNowIso() -> str:
        return datetime.now(timezone.utc).isoformat()

    @staticmethod
    def _writeJson(path: Path, payload: dict[str, Any]) -> None:
        temp_path = path.parent / f".{path.name}.tmp"
        with temp_path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=True, indent=2, sort_keys=True)
        temp_path.replace(path)


__all__ = ["CaseWorkspaceManager"]
