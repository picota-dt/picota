from __future__ import annotations

import argparse
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
    return parser


def main() -> None:
    args = build_parser().parse_args()
    TrainingService.runHttpService(
        host=args.host,
        port=int(args.port),
        workspace_dir=args.workspace_dir,
    )


if __name__ == "__main__":
    main()
