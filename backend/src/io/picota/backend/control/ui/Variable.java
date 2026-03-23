package io.picota.backend.control.ui;

public record Variable(
		String id,
		String name,
		String unit,
		Double value,
		VariableType variableType
) {
}
