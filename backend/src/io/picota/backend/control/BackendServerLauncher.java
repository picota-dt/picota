package io.picota.backend.control;

import io.intino.alexandria.logger4j.Logger;
import io.picota.backend.control.commands.UiCommands;
import io.picota.backend.control.commands.UiCommandsFactory;
import io.picota.backend.control.commands.UiCommandsMode;
import io.picota.backend.persistence.ModelPersistence;
import io.picota.backend.persistence.PersistenceConfig;
import io.picota.backend.persistence.PersistenceEngine;
import io.picota.backend.persistence.PersistenceFactory;
import org.apache.log4j.Level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public final class BackendServerLauncher {
	private BackendServerLauncher() {
	}

	public static void main(String[] args) {
		Logger.init(Level.ERROR);
		Path configPath = configPathFromArgs(args);
		String jdbcUrlOverride = dbUrlFromArgs(args);
		Properties properties = loadProperties(configPath);
		Path configBaseDir = configPath.getParent() == null
				? Paths.get(".").toAbsolutePath().normalize()
				: configPath.getParent();
		BackendWebServer.Config config = loadConfig(properties, configBaseDir);
		PersistenceConfig persistenceConfig = loadPersistenceConfig(properties, config.workdir(), jdbcUrlOverride);
		ModelPersistence persistence = PersistenceFactory.create(persistenceConfig);
		UiCommands commands = UiCommandsFactory.create(loadCommandsMode(properties), persistence);
		BackendWebServer server = new BackendWebServer(config, commands);
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			server.stop();
			persistence.close();
		}));
	}

	private static Properties loadProperties(Path configPath) {
		Properties defaults = defaultProperties();
		if (!Files.exists(configPath)) {
			throw new IllegalArgumentException("Properties file does not exist: " + configPath.toAbsolutePath());
		}
		Properties loaded = new Properties(defaults);
		try (InputStream in = Files.newInputStream(configPath)) {
			loaded.load(in);
			return loaded;
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load properties from: " + configPath, e);
		}
	}

	private static Path configPathFromArgs(String[] args) {
		if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
			throw new IllegalArgumentException("First argument must be the path to the properties file");
		}
		return Paths.get(args[0].trim()).toAbsolutePath().normalize();
	}

	private static String dbUrlFromArgs(String[] args) {
		if (args == null || args.length < 2) return "";
		for (int i = 1; i < args.length; i++) {
			String raw = args[i];
			if (raw == null || raw.isBlank()) continue;
			String value = raw.trim();
			if (value.startsWith("--db-url=")) {
				return value.substring("--db-url=".length()).trim();
			}
			if ("--db-url".equals(value) && i + 1 < args.length && args[i + 1] != null) {
				return args[i + 1].trim();
			}
		}
		String secondArg = args[1];
		if (secondArg == null) return "";
		String trimmed = secondArg.trim();
		return trimmed.startsWith("--") ? "" : trimmed;
	}

	private static Properties defaultProperties() {
		Properties p = new Properties();
		p.setProperty("app.host", "0.0.0.0");
		p.setProperty("app.port", "8080");
		p.setProperty("app.workdir", "./runtime");
		p.setProperty("app.mode", "real");
		p.setProperty("app.api.prefix", "/v1");
		p.setProperty("app.db.engine", "sqlite");
		p.setProperty("app.db.sqlite.file", "picota.db");
		p.setProperty("app.db.mysql.host", "127.0.0.1");
		p.setProperty("app.db.mysql.port", "3306");
		p.setProperty("app.db.mysql.database", "picota");
		p.setProperty("app.db.mysql.user", "picota");
		p.setProperty("app.db.mysql.password", "picota");
		return p;
	}

	private static BackendWebServer.Config loadConfig(Properties properties, Path baseDir) {
		String host = property(properties, "app.host");
		int port = intProperty(properties, "app.port", 8080);
		Path workdir = resolvePath(baseDir, property(properties, "app.workdir"));
		String apiPrefix = property(properties, "app.api.prefix");

		return new BackendWebServer.Config(
				host,
				port,
				workdir,
				apiPrefix
		);
	}

	private static PersistenceConfig loadPersistenceConfig(Properties properties, Path appWorkdir, String jdbcUrlOverride) {
		PersistenceEngine engine = PersistenceEngine.fromValue(property(properties, "app.db.engine"), PersistenceEngine.SQLITE);
		if (jdbcUrlOverride != null && !jdbcUrlOverride.isBlank()) {
			engine = engineFromJdbcUrl(jdbcUrlOverride, engine);
		}
		String sqliteRaw = property(properties, "app.db.sqlite.file");
		String sqliteFileName = sqliteRaw.isBlank()
				? "picota.db"
				: Path.of(sqliteRaw).getFileName().toString();
		Path sqliteFile = appWorkdir.resolve(sqliteFileName).toAbsolutePath().normalize();

		String mysqlHost = property(properties, "app.db.mysql.host");
		int mysqlPort = intProperty(properties, "app.db.mysql.port", 3306);
		String mysqlDatabase = property(properties, "app.db.mysql.database");
		String mysqlUser = property(properties, "app.db.mysql.user");
		String mysqlPassword = property(properties, "app.db.mysql.password");

		return new PersistenceConfig(engine, sqliteFile, mysqlHost, mysqlPort, mysqlDatabase, mysqlUser, mysqlPassword, jdbcUrlOverride);
	}

	private static PersistenceEngine engineFromJdbcUrl(String jdbcUrl, PersistenceEngine fallback) {
		String normalized = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("jdbc:sqlite:")) return PersistenceEngine.SQLITE;
		if (normalized.startsWith("jdbc:mysql:")) return PersistenceEngine.MYSQL;
		return fallback;
	}

	private static Path resolvePath(Path baseDir, String rawPath) {
		if (rawPath == null || rawPath.isBlank()) {
			return baseDir.toAbsolutePath().normalize();
		}
		Path path = Paths.get(rawPath.trim());
		if (path.isAbsolute()) {
			return path.normalize();
		}
		return baseDir.resolve(path).toAbsolutePath().normalize();
	}

	private static UiCommandsMode loadCommandsMode(Properties properties) {
		return UiCommandsMode.fromValue(property(properties, "app.mode"), UiCommandsMode.REAL);
	}

	private static String property(Properties properties, String key) {
		String fromFile = properties.getProperty(key);
		return fromFile == null ? "" : fromFile.trim();
	}

	private static int intProperty(Properties properties, String key, int defaultValue) {
		try {
			return Integer.parseInt(property(properties, key));
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}
}
