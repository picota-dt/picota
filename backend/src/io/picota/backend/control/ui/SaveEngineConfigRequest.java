package io.picota.backend.control.ui;

public record SaveEngineConfigRequest(
		TrainingAlgorithm algorithm,
		Integer epochs,
		Double learningRate,
		Integer windowSize,
		Integer batchSize
) {
}
