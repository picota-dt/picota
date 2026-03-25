package io.picota.backend.control.ingestion;

import io.picota.backend.control.commands.demo.DemoCommandState;

public final class DemoIngestSensorMetricsCommand implements IngestSensorMetricsCommand {
	private final DemoCommandState state;

	public DemoIngestSensorMetricsCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void ingestSubjectSensorMetrics(String authToken, String twinId, String subjectId, IngestMetricsRequest request) {
		state.ingestSubjectSensorMetrics(authToken, twinId, subjectId, request);
	}
}
