import os

import trainer.house.temperature as temperature
from kan.DatasetLoader import DatasetLoader


def train(source, modelsdir):
    subjects = {"House"}
    for s in subjects:
        jsonl_path = os.path.join(source, s + "_Temperature.jsonl")
        if not os.path.exists(jsonl_path):
            print(f"{s}	Temperature	NaN", flush=True)
            continue
        loader = DatasetLoader(jsonl_path)
        temperature.train(
            s,
            loader.load(),
            loader.get_input_variables(),
            loader.get_means(),
            loader.get_stds(),
            loader.get_out_min(),
            loader.get_out_max(),
            modelsdir + "/" + s
        )
