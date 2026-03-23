package io.picota.backend.control.ui;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TwinType {
	MACHINE("Machine"),
	BUILDING("Building"),
	INFRASTRUCTURE("Infrastructure"),
	ENERGY_SYSTEM("Energy System"),
	PIPELINE("Pipeline"),
	ROBOT("Robot"),
	SENSOR_NETWORK("Sensor Network"),
	OTHER("Other");

	private final String wireValue;

	TwinType(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static TwinType fromWireValue(String value) {
		if (value == null) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown TwinType: " + value));
	}
}
