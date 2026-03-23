package io.picota.backend.control.ui;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum VersionBump {
	PATCH("patch"),
	MINOR("minor"),
	MAJOR("major");

	private final String wireValue;

	VersionBump(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static VersionBump fromWireValue(String value) {
		if (value == null) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown VersionBump: " + value));
	}
}
