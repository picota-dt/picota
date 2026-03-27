from __future__ import annotations

import math

import numpy as np

from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow


class DatasetSplitter:
    def __init__(self, *, seed: int, train_ratio: float, val_ratio: float, test_ratio: float):
        self.seed = int(seed)
        self.train_ratio = float(train_ratio)
        self.val_ratio = float(val_ratio)
        self.test_ratio = float(test_ratio)

    def split(self, rows: list[ExampleRow]) -> tuple[list[ExampleRow], list[ExampleRow], list[ExampleRow]]:
        if not math.isclose(self.train_ratio + self.val_ratio + self.test_ratio, 1.0, rel_tol=1e-6, abs_tol=1e-6):
            raise ValueError("train_ratio + val_ratio + test_ratio must be 1.0")
        rng = np.random.default_rng(self.seed)
        idx = np.arange(len(rows))
        rng.shuffle(idx)

        train_end = max(1, int(len(rows) * self.train_ratio))
        val_end = min(len(rows) - 1, train_end + max(1, int(len(rows) * self.val_ratio)))
        if train_end >= val_end:
            train_end = max(1, val_end - 1)

        train_rows = [rows[int(i)] for i in idx[:train_end]]
        val_rows = [rows[int(i)] for i in idx[train_end:val_end]]
        test_rows = [rows[int(i)] for i in idx[val_end:]]
        if len(test_rows) == 0:
            test_rows = [rows[int(idx[-1])]]
        return train_rows, val_rows, test_rows
