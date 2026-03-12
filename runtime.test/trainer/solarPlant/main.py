from __future__ import annotations

import argparse
import importlib.util
import os
import sys
from pathlib import Path

from kan.DatasetLoader import DatasetLoader

import trainer
import trainer.solarPlant.generatedActivePower
import trainer.solarPlant.generatedReactivePower

DEFAULT_SOURCE = "/Users/oroncal/workspace/projects/picota/temp/data/infecar"
DEFAULT_MODELS_DIR = "/Users/oroncal/workspace/projects/picota/temp/test-models"


def train(source, modelsdir):
    subjects = {"SolarPlant"}
    for s in subjects:
        jsonl_path = os.path.join(source, s + "_generatedActivePower.jsonl")
        if not os.path.exists(jsonl_path):
            print(f"{s}	generatedActivePower	NaN", flush=True)
            continue
        loader = DatasetLoader(jsonl_path)
        trainer.solarPlant.generatedActivePower.train(
            s,
            loader.load(),
            loader.get_input_variables(),
            loader.get_means(),
            loader.get_stds(),
            loader.get_out_min(),
            loader.get_out_max(),
            modelsdir + "/" + s
        )
    subjects = {"SolarPlant"}
    for s in subjects:
        jsonl_path = os.path.join(source, s + "_generatedReactivePower.jsonl")
        if not os.path.exists(jsonl_path):
            print(f"{s}	generatedReactivePower	NaN", flush=True)
            continue
        loader = DatasetLoader(jsonl_path)
        trainer.solarPlant.generatedReactivePower.train(
            s,
            loader.load(),
            loader.get_input_variables(),
            loader.get_means(),
            loader.get_stds(),
            loader.get_out_min(),
            loader.get_out_max(),
            modelsdir + "/" + s
        )


def _ensure_local_paths() -> None:
    runtime_test_root = Path(__file__).resolve().parents[1]
    runtime_trainer_root = Path(__file__).resolve().parents[2] / "runtime.trainer"

    if str(runtime_test_root) not in sys.path:
        sys.path.insert(0, str(runtime_test_root))

    if importlib.util.find_spec("kan") and importlib.util.find_spec("Device"):
        return

    if runtime_trainer_root.exists() and str(runtime_trainer_root) not in sys.path:
        sys.path.insert(0, str(runtime_trainer_root))

    if importlib.util.find_spec("kan") and importlib.util.find_spec("Device"):
        return

    raise ModuleNotFoundError(
        "No se pudo cargar la dependencia compartida desde runtime.trainer "
        "(modulos 'kan' y 'Device'). Ejecuta ./bootstrap.sh dentro de runtime.test."
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Train SolarPlant models")
    parser.add_argument("source", nargs="?", default=DEFAULT_SOURCE)
    parser.add_argument("models_dir", nargs="?", default=DEFAULT_MODELS_DIR)
    args = parser.parse_args()

    _ensure_local_paths()

    from trainer.solarPlant import main as solar_plant_main

    solar_plant_main.train(args.source, args.models_dir)


if __name__ == "__main__":
    main()
