package io.picota.backend.control.ingestion;

import io.picota.backend.control.commands.real.RealCommandState;

public final class RealIngestSensorMetricsCommand implements IngestSensorMetricsCommand {
	private final RealCommandState state;

	public RealIngestSensorMetricsCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void ingestSubjectSensorMetrics(String authToken, String twinId, String subjectId, IngestMetricsRequest request) {
		state.ingestSubjectSensorMetrics(authToken, twinId, subjectId, request);
	}
}
