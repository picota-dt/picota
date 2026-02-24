import os

import trainer.solarPlant.generatedActivePower
import trainer.solarPlant.generatedReactivePower
from trainer.kan.DatasetLoader import DatasetLoader


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
