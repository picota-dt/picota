package io.picota.backend.model;

import java.time.Instant;

public record TrainingJob(
		String jobId,
		String twinId,
		TrainingJobStatus status,
		Integer progress,
		String currentPhase,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		String errorMessage,
		InferenceEngine result
) {
}
