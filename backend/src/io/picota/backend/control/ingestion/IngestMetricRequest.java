package io.picota.backend.control.ingestion;

import java.time.Instant;

public record IngestMetricRequest(
		String variableId,
		String variableName,
		Instant instant,
		Double value
) {
}
