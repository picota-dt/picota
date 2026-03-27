package io.picota.backend.control;

import io.intino.alexandria.logger4j.Logger;
import io.picota.backend.control.auth.GoogleAuthConfig;
import io.picota.backend.control.auth.GoogleIdTokenVerifierService;
import io.picota.backend.control.auth.GoogleIdentityVerifier;
import io.picota.backend.control.auth.StubGoogleIdentityVerifier;
import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.commands.UiCommandSet;
import io.picota.backend.control.commands.UiCommandsFactory;
import io.picota.backend.control.commands.UiCommandsMode;
import io.picota.backend.control.training.ExternalTrainingClient;
import io.picota.backend.control.training.HttpExternalTrainingClient;
import io.picota.backend.control.ui.BackendWebServer;
import io.picota.backend.persistence.ModelPersistence;
import io.picota.backend.persistence.PersistenceConfig;
import io.picota.backend.persistence.PersistenceEngine;
import io.picota.backend.persistence.PersistenceFactory;
import org.apache.log4j.Level;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

public final class Main {
	private Main() {
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
		TwinModelTemplate twinModelTemplate = loadTwinModelTemplate(properties, configBaseDir);
		Path datasetsRootDir = loadDatasetsRootDir(properties, config.workdir());
		UiCommandsMode mode = loadCommandsMode(properties);
		ExternalTrainingClient trainingClient = loadTrainingClient(properties, mode);
		GoogleIdentityVerifier googleIdentityVerifier = loadGoogleIdentityVerifier(properties, mode);
		ModelPersistence persistence = PersistenceFactory.create(persistenceConfig);
		UiCommandSet commands = UiCommandsFactory.create(mode, persistence, twinModelTemplate, datasetsRootDir, trainingClient, googleIdentityVerifier);
		BackendWebServer server = new BackendWebServer(config, commands);
		printStartupBanner(configPath, config, mode);
		server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			server.stop();
			persistence.close();
		}));
	}

	private static void printStartupBanner(Path configPath, BackendWebServer.Config config, UiCommandsMode mode) {
		String banner = """
				 ____ ___ ____ ___ _____   _
				|  _ \\_ _/ ___/ _ \\_   _/ / \\
				| |_) | | |  | | | || |  / _ \\
				|  __/| | |__| |_| || | / ___ \\
				|_|  |___\\____\\___/ |_|/_/   \\_\\
				""";
		System.out.println(banner);
		System.out.println("PICOTA backend arrancando...");
		System.out.println("Config: " + configPath);
		System.out.println("Modo: " + mode.name());
		System.out.println("Endpoint: http://" + config.host() + ":" + config.port() + config.apiPrefix());
		System.out.println("Workdir: " + config.workdir());
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

	private static TwinModelTemplate loadTwinModelTemplate(Properties properties, Path baseDir) {
		String rawTemplatePath = property(properties, "app.twin.model.template.file");
		if (rawTemplatePath.isBlank()) return TwinModelTemplate.defaultTemplate();
		Path templatePath = resolvePath(baseDir, rawTemplatePath);
		if (templatePath == null) return TwinModelTemplate.defaultTemplate();
		try {
			return TwinModelTemplate.fromRaw(Files.readString(templatePath));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load model template from: " + templatePath.toAbsolutePath(), e);
		}
	}

	private static Properties defaultProperties() {
		Properties p = new Properties();
		p.setProperty("app.host", "0.0.0.0");
		p.setProperty("app.port", "8080");
		p.setProperty("app.workdir", "./runtime");
		p.setProperty("app.mode", "real");
		p.setProperty("app.api.prefix", "/v1");
		p.setProperty("app.datasets.dir", "datasets");
		p.setProperty("app.training.api.base-url", "");
		p.setProperty("app.training.api.timeout.seconds", "30");
		p.setProperty("app.auth.google.client-id", "");
		p.setProperty("app.db.engine", "sqlite");
		p.setProperty("app.db.sqlite.file", "picota.db");
		p.setProperty("app.db.mysql.host", "127.0.0.1");
		p.setProperty("app.db.mysql.port", "3306");
		p.setProperty("app.db.mysql.database", "picota");
		p.setProperty("app.db.mysql.user", "picota");
		p.setProperty("app.db.mysql.password", "picota");
		p.setProperty("app.twin.model.template.file", "");
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

	private static Path loadDatasetsRootDir(Properties properties, Path appWorkdir) {
		String rawDatasetsDir = property(properties, "app.datasets.dir");
		if (rawDatasetsDir.isBlank()) {
			return appWorkdir.resolve("datasets").toAbsolutePath().normalize();
		}
		Path datasetsPath = Paths.get(rawDatasetsDir.trim());
		if (datasetsPath.isAbsolute()) {
			return datasetsPath.normalize();
		}
		return appWorkdir.resolve(datasetsPath).toAbsolutePath().normalize();
	}

	private static ExternalTrainingClient loadTrainingClient(Properties properties, UiCommandsMode mode) {
		String baseUrl = property(properties, "app.training.api.base-url");
		if (baseUrl.isBlank()) {
			if (mode == UiCommandsMode.REAL) {
				throw new IllegalArgumentException(
						"Missing required property 'app.training.api.base-url' for real mode. " +
								"Set a valid http(s) URL to the training API."
				);
			}
			return ExternalTrainingClient.disabled();
		}
		String normalizedBaseUrl = validateTrainingApiBaseUrl(baseUrl);
		int timeoutSeconds = Math.max(1, intProperty(properties, "app.training.api.timeout.seconds", 30));
		return new HttpExternalTrainingClient(normalizedBaseUrl, Duration.ofSeconds(timeoutSeconds));
	}

	private static GoogleIdentityVerifier loadGoogleIdentityVerifier(Properties properties, UiCommandsMode mode) {
		String clientId = property(properties, "app.auth.google.client-id");
		if (clientId.isBlank()) {
			if (mode == UiCommandsMode.REAL) {
				throw new IllegalArgumentException(
						"Missing required property 'app.auth.google.client-id' for real mode. " +
								"Set the Google Web client id used by the frontend."
				);
			}
			return new StubGoogleIdentityVerifier(new GoogleAuthConfig("demo-google-client-id"), null);
		}
		return new GoogleIdTokenVerifierService(new GoogleAuthConfig(clientId));
	}

	private static String validateTrainingApiBaseUrl(String rawBaseUrl) {
		String value = rawBaseUrl == null ? "" : rawBaseUrl.trim();
		URI uri;
		try {
			uri = URI.create(value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid 'app.training.api.base-url': " + rawBaseUrl, e);
		}
		if (!uri.isAbsolute()) {
			throw new IllegalArgumentException(
					"Invalid 'app.training.api.base-url': must be an absolute URL (http/https), got '" + rawBaseUrl + "'"
			);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException(
					"Invalid 'app.training.api.base-url': only http/https schemes are supported, got '" + rawBaseUrl + "'"
			);
		}
		if (uri.getHost() == null || uri.getHost().isBlank()) {
			throw new IllegalArgumentException(
					"Invalid 'app.training.api.base-url': host is missing in '" + rawBaseUrl + "'"
			);
		}
		return value;
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
