package io.picota.backend.model;

public record Variable(
		String id,
		String name,
		String unit,
		Double value,
		VariableType variableType
) {
}
