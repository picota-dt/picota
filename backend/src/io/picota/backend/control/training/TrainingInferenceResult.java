package io.picota.backend.control.training;

import java.time.Instant;

public record TrainingInferenceResult(
		String outputVariable,
		Double prediction,
		Instant inferredAt
) {
}
