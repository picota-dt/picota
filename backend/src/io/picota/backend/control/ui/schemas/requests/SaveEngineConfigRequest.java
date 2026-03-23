package io.picota.backend.control.ui.schemas.requests;

import io.picota.backend.control.ui.schemas.TrainingAlgorithm;

public record SaveEngineConfigRequest(
		TrainingAlgorithm algorithm,
		Integer epochs,
		Double learningRate,
		Integer windowSize,
		Integer batchSize
) {
}
