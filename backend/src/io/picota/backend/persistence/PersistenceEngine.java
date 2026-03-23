package io.picota.backend.persistence;

public enum PersistenceEngine {
	SQLITE,
	MYSQL;

	public static PersistenceEngine fromValue(String raw, PersistenceEngine fallback) {
		if (raw == null || raw.isBlank()) return fallback;
		try {
			return PersistenceEngine.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}
}
