from __future__ import annotations

import argparse
import logging
from pathlib import Path

from picota.framework.control.TrainingService import TrainingService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Picota training REST service")
    parser.add_argument("--host", type=str, default="0.0.0.0", help="Bind host")
    parser.add_argument("--port", type=int, default=8080, help="Bind port")
    parser.add_argument(
        "--workspace-dir",
        type=Path,
        default=None,
        help="Workspace directory where cases, datasets and tickets are persisted",
    )
    parser.add_argument(
        "--log-level",
        type=str,
        default=None,
        help="Log level: DEBUG, INFO, WARNING, ERROR, CRITICAL",
    )
    parser.add_argument(
        "--log-api-requests",
        action="store_true",
        help="Log inbound API requests and response status",
    )
    return parser


def configure_logging(level_name: str | None, *, log_api_requests: bool) -> None:
    level_text = (level_name or "").strip().upper()
    if not level_text:
        level_text = "INFO" if log_api_requests else "WARNING"
    level = getattr(logging, level_text, None)
    if not isinstance(level, int):
        raise ValueError(f"Invalid --log-level value: {level_name}")
    if log_api_requests and level > logging.INFO:
        level = logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s | %(message)s",
        force=True,
    )


def main() -> None:
    args = build_parser().parse_args()
    configure_logging(args.log_level, log_api_requests=bool(args.log_api_requests))
    TrainingService.runHttpService(
        host=args.host,
        port=int(args.port),
        workspace_dir=args.workspace_dir,
        log_api_requests=bool(args.log_api_requests),
    )


if __name__ == "__main__":
    main()
