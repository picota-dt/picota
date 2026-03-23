package io.picota.backend.control.ui;

import java.util.List;

public record VariableTelemetry(
		String variableId,
		String variableName,
		String unit,
		Double current,
		List<TelemetryPoint> history
) {
	public VariableTelemetry {
		history = history == null ? List.of() : List.copyOf(history);
	}
}
