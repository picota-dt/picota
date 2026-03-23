package io.picota.backend.control.ui.schemas;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TwinStatus {
	ACTIVE("active"),
	DRAFT("draft"),
	OFFLINE("offline");

	private final String wireValue;

	TwinStatus(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static TwinStatus fromWireValue(String value) {
		if (value == null) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown TwinStatus: " + value));
	}
}
