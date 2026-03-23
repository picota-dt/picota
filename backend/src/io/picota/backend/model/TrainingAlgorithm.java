package io.picota.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TrainingAlgorithm {
	KAN("KAN"),
	TABNET("TabNet");

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
				.filter(v -> v.wireValue.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown TrainingAlgorithm: " + value));
	}
}
