package io.picota.backend.control.ingestion;

import java.util.List;

public record IngestMetricsRequest(
		List<IngestMetricRequest> metrics
) {
	public IngestMetricsRequest {
		metrics = metrics == null ? List.of() : List.copyOf(metrics);
	}
}
