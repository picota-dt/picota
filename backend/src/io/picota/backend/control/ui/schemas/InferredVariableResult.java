package io.picota.backend.control.ui.schemas;

import java.util.Map;

public record InferredVariableResult(
		String name,
		Double mae,
		Double r2,
		Integer validationSampleCount,
		Double validationDurationSeconds,
		VariableDataType dataType,
		Double accuracy,
		Double macroF1,
		Double violations,
		Map<String, Double> constraintViolations
) {
	public InferredVariableResult {
		constraintViolations = constraintViolations == null ? Map.of() : Map.copyOf(constraintViolations);
	}
}
