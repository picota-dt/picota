import json


class DatasetLoader:
    def __init__(self, path):
        self.stds = None
        self.means = None
        self.path = path

    def load(self):
        data = []
        with open(self.path, 'r', encoding='utf-8') as f:
            first_line = next(f).strip()
            if not first_line:
                raise ValueError("The file is empty or means and stds are missing.")
            stats = json.loads(first_line)
            self.means = stats.get("means")
            self.stds  = stats.get("stds")
            if self.means is None or self.stds is None:
                raise KeyError("First line should contain 'means' y 'stds'.")
            for idx, line in f:
                line = line.strip()
                if not line:
                    continue
                obj = json.loads(line)
                data.append(obj)
        return data

    def means(self):
        return self.means


    def stds(self):
        return self.stds
