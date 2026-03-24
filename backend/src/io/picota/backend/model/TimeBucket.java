package io.picota.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

public enum TimeBucket {
	YEARS("years"),
	MONTHS("months"),
	DAYS("days"),
	HOURS("hours"),
	MINUTES("minutes"),
	SECONDS("seconds");

	private final String wireValue;

	TimeBucket(String wireValue) {
		this.wireValue = wireValue;
	}

	@JsonValue
	public String toWireValue() {
		return wireValue;
	}

	@JsonCreator
	public static TimeBucket fromWireValue(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = normalize(value);
		if ("none".equals(normalized)) return null;
		return Arrays.stream(values())
				.filter(v -> v.wireValue.equals(normalized) || v.name().equalsIgnoreCase(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown TimeBucket: " + value));
	}

	private static String normalize(String raw) {
		String normalized = raw.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "year" -> "years";
			case "month" -> "months";
			case "day" -> "days";
			case "hour" -> "hours";
			case "minute" -> "minutes";
			case "second" -> "seconds";
			default -> normalized;
		};
	}
}
