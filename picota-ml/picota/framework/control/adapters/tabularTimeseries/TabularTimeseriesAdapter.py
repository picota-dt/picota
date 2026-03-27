from __future__ import annotations

import logging
from pathlib import Path

from picota.framework.control.adapters.tabularTimeseries.ColumnResolver import ColumnResolver
from picota.framework.control.adapters.tabularTimeseries.DatasetSplitter import DatasetSplitter
from picota.framework.control.adapters.tabularTimeseries.ExampleRow import ExampleRow
from picota.framework.control.adapters.tabularTimeseries.ItemEncoder import ItemEncoder
from picota.framework.control.adapters.tabularTimeseries.NumericalScalerFactory import NumericalScalerFactory
from picota.framework.control.adapters.tabularTimeseries.RequestOptionsParser import RequestOptionsParser
from picota.framework.control.adapters.tabularTimeseries.TabularDataLoader import TabularDataLoader
from picota.framework.control.adapters.tabularTimeseries.TemporalDatasetBuilder import TemporalDatasetBuilder
from picota.framework.model.TrainingRequest import TrainingRequest
from picota.framework.model.data.PreparedTrainingData import PreparedTrainingData

logger = logging.getLogger(__name__)


class TabularTimeseriesAdapter:
    def __init__(self, request: TrainingRequest):
        self.request = request
        self.options_parser = RequestOptionsParser()
        self.data_loader = TabularDataLoader()
        self.scaler_factory = NumericalScalerFactory()

    def prepare(self) -> PreparedTrainingData:
        source_path = self._resolve_source_path()
        logger.info("Loading tabular_timeseries source: %s", str(source_path))
        options = self.options_parser.parse(self.request)
        raw_rows, headers, _delimiter = self.data_loader.load(source_path, explicit_delimiter=options.delimiter)

        if self.request.data_source.limit_rows is not None:
            raw_rows = raw_rows[: int(self.request.data_source.limit_rows)]
        logger.info("Raw rows loaded: %s", len(raw_rows))
        if len(raw_rows) < 10:
            raise ValueError(f"Need >=10 raw rows, got {len(raw_rows)}")

        selection = ColumnResolver(headers=headers, rows=raw_rows, options=options).resolve()
        if len(selection.numerical_columns) == 0:
            raise ValueError("At least one numerical input column is required")

        dataset_builder = TemporalDatasetBuilder(
            options=options,
            numerical_columns=selection.numerical_columns,
            categorical_columns=selection.categorical_columns,
        )
        aggregated_rows = dataset_builder.aggregate_rows(raw_rows)
        logger.info("Aggregated rows: %s", len(aggregated_rows))
        if len(aggregated_rows) < 10:
            raise ValueError(f"Need >=10 aggregated rows, got {len(aggregated_rows)}")

        horizon_timestamp_resolver = dataset_builder.resolve_horizon_delta(
            value=self.request.time_horizon.value,
            unit=self.request.time_horizon.unit,
            time_bucket=options.time_bucket,
        )
        horizon_rows = dataset_builder.build_horizon_examples(
            aggregated_rows,
            horizon_timestamp_resolver=horizon_timestamp_resolver
        )
        logger.info(
            "Rows after horizon pairing: %s (horizon_value=%s, horizon_unit=%s, time_bucket=%s)",
            len(horizon_rows),
            self.request.time_horizon.value,
            self.request.time_horizon.unit,
            options.time_bucket,
        )
        if len(horizon_rows) < 10:
            raise ValueError(f"Need >=10 rows after horizon pairing, got {len(horizon_rows)}")

        train_rows, val_rows, test_rows = DatasetSplitter(
            seed=self.request.architecture.seed,
            train_ratio=self.request.split.train_ratio,
            val_ratio=self.request.split.val_ratio,
            test_ratio=self.request.split.test_ratio,
        ).split(horizon_rows)
        logger.info(
            "Dataset split completed (train=%s, val=%s, test=%s)",
            len(train_rows),
            len(val_rows),
            len(test_rows),
        )

        target_min, target_max = self._fit_target_minmax(train_rows)
        scaler = self.scaler_factory.build(
            rows=train_rows,
            numerical_columns=selection.numerical_columns,
            scaler_kind=options.numerical_scaler,
        )

        one_hot_map: dict[str, list[str]] = {}
        if options.categorical_encoding == "one_hot" and len(selection.categorical_columns) > 0:
            one_hot_map = ItemEncoder.build_one_hot_map(train_rows, selection.categorical_columns)

        time_features_kind = ItemEncoder.time_features_kind_from_bucket(options.time_bucket)
        one_hot_feature_names = ItemEncoder.build_one_hot_feature_names(one_hot_map)
        time_feature_names = ItemEncoder.time_feature_names(time_features_kind)

        encoder = ItemEncoder(
            time_features_kind=time_features_kind,
            numerical_columns=selection.numerical_columns,
            numerical_value_getter=scaler.value_getter,
            one_hot_map=one_hot_map,
            target_min=target_min,
            target_max=target_max,
        )
        train_items = encoder.build_items(train_rows)
        val_items = encoder.build_items(val_rows)
        test_items = encoder.build_items(test_rows)

        input_variables = [*time_feature_names, *selection.numerical_columns, *one_hot_feature_names]
        logger.info(
            "Prepared training data (job=%s, case=%s, numerical=%s, categorical=%s)",
            self.request.job_name,
            options.case_name,
            len(selection.numerical_columns),
            len(selection.categorical_columns),
        )

        return PreparedTrainingData(
            job_name=self.request.job_name,
            case_name=options.case_name,
            input_variables=input_variables,
            output_variable=self.request.target_variable,
            lookback=self.request.lookback,
            means=scaler.means,
            stds=scaler.stds,
            out_min=target_min,
            out_max=target_max,
            train_items=train_items,
            val_items=val_items,
            test_items=test_items,
            metadata={
                "dataset_path": str(source_path),
                "data_source_kind": self.request.data_source.kind,
                "case_name": options.case_name,
                "time_bucket": options.time_bucket,
                "horizon_value": self.request.time_horizon.value,
                "horizon_unit": self.request.time_horizon.unit,
                "parsed_rows": len(raw_rows),
                "aggregated_rows": len(aggregated_rows),
                "horizon_rows": len(horizon_rows),
                "train_rows": len(train_rows),
                "val_rows": len(val_rows),
                "test_rows": len(test_rows),
                "numerical_columns": list(selection.numerical_columns),
                "categorical_columns": list(selection.categorical_columns),
                "numerical_scaler": options.numerical_scaler,
                "categorical_encoding": options.categorical_encoding,
                "numerical_stats": scaler.stats,
                "feature_names": {
                    "t": list(time_feature_names),
                    "numerical_t_features": list(selection.numerical_columns),
                    "categorical_t_features": list(one_hot_feature_names),
                    "lookback_t": [],
                    "numerical_lookback_features": [],
                    "categorical_lookback_features": [],
                },
                "entity_key_columns": list(options.entity_key_columns),
            },
        )

    def _resolve_source_path(self) -> Path:
        source_path = self.request.data_source.resolved_path()
        if source_path is None:
            raise FileNotFoundError("data_source.path is required for 'tabular_timeseries'")
        if not source_path.exists():
            raise FileNotFoundError(f"Dataset not found: {source_path}")
        return source_path

    @staticmethod
    def _fit_target_minmax(rows: list[ExampleRow]) -> tuple[float, float]:
        values = [row.target_future for row in rows]
        target_min = float(min(values))
        target_max = float(max(values))
        if target_max <= target_min:
            target_max = target_min + 1.0
        return target_min, target_max
