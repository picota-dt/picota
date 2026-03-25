package io.picota.backend.control.ingestion;

@FunctionalInterface
public interface IngestSensorMetricsCommand {
	void ingestSubjectSensorMetrics(String authToken, String twinId, String subjectId, IngestMetricsRequest request);
}
