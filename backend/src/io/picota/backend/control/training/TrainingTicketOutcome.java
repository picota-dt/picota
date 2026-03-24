package io.picota.backend.control.training;

import java.util.Map;

public record TrainingTicketOutcome(
		String outputVariable,
		Double r2,
		Double maeRaw,
		Integer testSamples,
		Double testElapsedSeconds,
		Double accuracy,
		Double macroF1,
		Double overallViolationRate,
		Map<String, Double> constraintViolationRates
) {
	public TrainingTicketOutcome {
		constraintViolationRates = constraintViolationRates == null ? Map.of() : Map.copyOf(constraintViolationRates);
	}
}
