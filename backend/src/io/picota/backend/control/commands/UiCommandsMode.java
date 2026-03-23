package io.picota.backend.control.commands;

public enum UiCommandsMode {
	DEMO,
	REAL;

	public static UiCommandsMode fromValue(String raw, UiCommandsMode defaultMode) {
		if (raw == null || raw.isBlank()) return defaultMode;
		try {
			return UiCommandsMode.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return defaultMode;
		}
	}
}
