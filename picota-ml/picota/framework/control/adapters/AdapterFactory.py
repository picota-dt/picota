from __future__ import annotations

from picota.framework.control.adapters.tabularTimeseries.TabularTimeseriesAdapter import TabularTimeseriesAdapter
from picota.framework.model.TrainingRequest import TrainingRequest
from picota.framework.model.data.PreparedTrainingData import PreparedTrainingData


class AdapterFactory:
    @staticmethod
    def buildPreparedData(request: TrainingRequest) -> PreparedTrainingData:
        if request.data_source.kind == "tabular_timeseries":
            return TabularTimeseriesAdapter(request).prepare()
        raise ValueError(f"Unsupported data source kind: {request.data_source.kind}")
