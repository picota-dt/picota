package io.picota.backend.control.ui.schemas;

import java.time.Instant;
import java.util.Map;

public record SubjectDataset(
		String subjectId,
		String fileName,
		Integer uploadedRecords,
		Integer realtimeRecords,
		Instant uploadedAt,
		Map<String, VariableStat> stats
) {
	public SubjectDataset {
		stats = stats == null ? Map.of() : Map.copyOf(stats);
	}
}
