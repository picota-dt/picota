from __future__ import annotations

import csv
from pathlib import Path


class TabularDataLoader:
    def load(
            self,
            source_path: Path,
            *,
            explicit_delimiter: str | None,
    ) -> tuple[list[dict[str, str]], list[str], str]:
        delimiter = self._detect_delimiter(source_path, explicit=explicit_delimiter)
        rows, headers = self._load_raw_rows(source_path, delimiter=delimiter)
        return rows, headers, delimiter

    @staticmethod
    def _detect_delimiter(table_path: Path, *, explicit: str | None) -> str:
        if explicit is not None:
            if explicit == "\\t":
                return "\t"
            return explicit
        if table_path.suffix.lower() == ".tsv":
            return "\t"
        with table_path.open("r", encoding="utf-8", newline="") as handle:
            for raw_line in handle:
                line = raw_line.strip()
                if not line:
                    continue
                if "\t" in line:
                    return "\t"
                if ";" in line:
                    return ";"
                return ","
        return ","

    @staticmethod
    def _load_raw_rows(table_path: Path, *, delimiter: str) -> tuple[list[dict[str, str]], list[str]]:
        with table_path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle, delimiter=delimiter)
            headers = list(reader.fieldnames or [])
            rows: list[dict[str, str]] = []
            for row in reader:
                normalized_row: dict[str, str] = {}
                for key, value in row.items():
                    normalized_row[str(key)] = "" if value is None else str(value)
                rows.append(normalized_row)
        if len(headers) == 0:
            raise ValueError(f"Table has no header: {table_path}")
        return rows, headers
