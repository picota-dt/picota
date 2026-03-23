package io.picota.backend.control.ui.schemas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TrainingAlgorithm {
	LSTM("LSTM"),
	GRU("GRU"),
	TRANSFORMER("Transformer"),
	TCN("TCN"),
	MLP("MLP");

	private final String wireValue;

	TrainingAlgorithm(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static TrainingAlgorithm fromWireValue(String value) {
		if (value == null) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown TrainingAlgorithm: " + value));
	}
}
