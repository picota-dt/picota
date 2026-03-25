package io.picota.backend.control.training;

import java.util.List;
import java.util.Map;

public record TrainingTicketOutcome(
		String outputVariable,
		List<String> inputVariables,
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
		inputVariables = inputVariables == null ? List.of() : List.copyOf(inputVariables);
		constraintViolationRates = constraintViolationRates == null ? Map.of() : Map.copyOf(constraintViolationRates);
	}
}
