package io.picota.backend.control.ui.schemas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Variable(
		String id,
		String name,
		String description,
		String unit,
		VariableDataType dataType,
		VariableType variableType,
		Integer timeHorizon,
		Integer lookback
) {
	public Variable {
		dataType = dataType == null ? VariableDataType.NUMERIC : dataType;
		variableType = variableType == null ? VariableType.SENSOR : variableType;
		timeHorizon = timeHorizon == null || timeHorizon <= 0 ? null : timeHorizon;
		lookback = lookback == null || lookback < 0 ? null : lookback;
	}

	public Variable(
			String id,
			String name,
			String description,
			String unit,
			VariableDataType dataType,
			VariableType variableType
	) {
		this(id, name, description, unit, dataType, variableType, null, null);
	}
}
