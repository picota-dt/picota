package io.picota.backend.persistence;

import java.nio.file.Path;

public record PersistenceConfig(
		PersistenceEngine engine,
		Path sqliteFile,
		String mysqlHost,
		int mysqlPort,
		String mysqlDatabase,
		String mysqlUser,
		String mysqlPassword,
		String jdbcUrlOverride
) {
	public PersistenceConfig {
		engine = engine == null ? PersistenceEngine.SQLITE : engine;
		sqliteFile = sqliteFile == null ? Path.of("picota.db").toAbsolutePath().normalize() : sqliteFile.toAbsolutePath().normalize();
		mysqlHost = (mysqlHost == null || mysqlHost.isBlank()) ? "127.0.0.1" : mysqlHost.trim();
		mysqlPort = mysqlPort <= 0 ? 3306 : mysqlPort;
		mysqlDatabase = (mysqlDatabase == null || mysqlDatabase.isBlank()) ? "picota" : mysqlDatabase.trim();
		mysqlUser = mysqlUser == null ? "" : mysqlUser.trim();
		mysqlPassword = mysqlPassword == null ? "" : mysqlPassword;
		jdbcUrlOverride = jdbcUrlOverride == null ? "" : jdbcUrlOverride.trim();
	}

	public boolean hasJdbcUrlOverride() {
		return !jdbcUrlOverride.isBlank();
	}

	public String jdbcUrl() {
		if (hasJdbcUrlOverride()) return jdbcUrlOverride;
		return switch (engine) {
			case SQLITE -> "jdbc:sqlite:" + sqliteFile.toString();
			case MYSQL -> "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase +
					"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
		};
	}
}
