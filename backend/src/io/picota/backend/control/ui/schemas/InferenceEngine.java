package io.picota.backend.control.ui.schemas;

import java.time.Instant;
import java.util.List;

public record InferenceEngine(
		Boolean trained,
		TrainingAlgorithm algorithm,
		Instant trainedAt,
		Instant launchedAt,
		Double trainingDurationSeconds,
		Integer epochs,
		Double learningRate,
		Integer windowSize,
		Integer batchSize,
		List<InferredVariableResult> inferredVariables,
		RetrainingConfig retrainingConfig
) {
	public InferenceEngine {
		inferredVariables = inferredVariables == null ? List.of() : List.copyOf(inferredVariables);
	}
}
