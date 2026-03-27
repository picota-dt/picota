from __future__ import annotations

from picota.framework.control.adapters.tabularTimeseries.AdapterOptions import AdapterOptions
from picota.framework.control.adapters.tabularTimeseries.ColumnSelection import ColumnSelection


class ColumnResolver:
    def __init__(self, *, headers: list[str], rows: list[dict[str, str]], options: AdapterOptions):
        self.headers = headers
        self.rows = rows
        self.options = options

    def resolve(self) -> ColumnSelection:
        if self.options.timestamp_column not in self.headers:
            raise ValueError(f"Missing timestamp column '{self.options.timestamp_column}' in dataset header")
        if self.options.target_column not in self.headers:
            raise ValueError(f"Missing target column '{self.options.target_column}' in dataset header")

        for column in self.options.entity_key_columns:
            if column not in self.headers:
                raise ValueError(f"Missing entity key column '{column}' in dataset header")

        excluded = set(self.options.exclude_input_columns)
        excluded.add(self.options.timestamp_column)
        excluded.add(self.options.target_column)
        candidate_columns = [col for col in self.headers if col not in excluded]

        if self.options.numerical_input_columns is not None:
            numerical = list(self.options.numerical_input_columns)
        else:
            numerical = [
                col
                for col in candidate_columns
                if self._is_float_column([row.get(col, "") for row in self.rows])
            ]

        for col in numerical:
            if col not in self.headers:
                raise ValueError(f"Unknown numerical input column '{col}'")

        if self.options.categorical_input_columns is not None:
            categorical = list(self.options.categorical_input_columns)
        else:
            categorical = [col for col in candidate_columns if col not in set(numerical)]

        for col in categorical:
            if col not in self.headers:
                raise ValueError(f"Unknown categorical input column '{col}'")

        overlap = set(numerical) & set(categorical)
        if overlap:
            raise ValueError(f"Columns cannot be both numerical and categorical: {sorted(overlap)}")

        return ColumnSelection(numerical_columns=numerical, categorical_columns=categorical)

    @staticmethod
    def _is_float_column(values: list[str]) -> bool:
        for value in values:
            text = str(value).strip()
            if text == "":
                continue
            try:
                float(text)
            except ValueError:
                return False
        return True
