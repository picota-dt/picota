package io.picota.backend.control.training;

import java.time.Instant;
import java.util.List;

public record TrainingTicketSnapshot(
		String ticketId,
		String status,
		Instant createdAt,
		Instant updatedAt,
		Instant startedAt,
		Instant finishedAt,
		Integer epochsCompleted,
		Integer epochsTotal,
		Double progressPercent,
		TrainingTicketOutcome outcome,
		String errorMessage,
		List<String> historyStatuses
) {
	public TrainingTicketSnapshot {
		historyStatuses = historyStatuses == null ? List.of() : List.copyOf(historyStatuses);
	}
}
