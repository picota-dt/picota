package io.picota.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum RetrainingSchedule {
	DAILY("daily"),
	WEEKLY("weekly"),
	MONTHLY("monthly");

	private final String wireValue;

	RetrainingSchedule(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static RetrainingSchedule fromWireValue(String value) {
		if (value == null) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown RetrainingSchedule: " + value));
	}
}
