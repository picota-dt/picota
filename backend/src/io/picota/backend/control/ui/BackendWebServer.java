package io.picota.backend.control.ui;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandSet;
import io.picota.backend.control.commands.UiCommandsFactory;
import io.picota.backend.control.commands.UiCommandsMode;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.Error;
import io.picota.backend.control.ui.schemas.InferenceEngine;
import io.picota.backend.control.ui.schemas.RetrainingConfig;
import io.picota.backend.control.ui.schemas.requests.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.javalin.apibuilder.ApiBuilder.*;

public class BackendWebServer {
	private static final String FRONTEND_RESOURCES_DIR = "webapp";
	private static final String FRONTEND_INDEX_RESOURCE = FRONTEND_RESOURCES_DIR + "/index.html";

	private final Config config;
	private final UiCommandSet commands;
	private Javalin app;

	public BackendWebServer(Config config) {
		this(config, UiCommandsFactory.create(UiCommandsMode.REAL));
	}

	public BackendWebServer(Config config, UiCommandSet commands) {
		this.config = config == null ? Config.defaultConfig() : config;
		this.commands = commands == null ? UiCommandsFactory.create(UiCommandsMode.REAL) : commands;
	}

	public synchronized void start() {
		if (app != null) return;
		FrontendAssets frontendAssets = resolveFrontendAssets();
		boolean frontendAvailable = frontendAssets.available();

		app = Javalin.create(javalin -> {
			javalin.http.defaultContentType = "application/json";
			javalin.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
			if (frontendAvailable) {
				javalin.staticFiles.add(files -> {
					files.hostedPath = "/";
					files.directory = frontendAssets.directory();
					files.location = frontendAssets.location();
				});
			}

			javalin.routes.exception(UiCommandException.class, (e, ctx) -> writeError(ctx, e.statusCode(), e.code(), e.getMessage(), e.details()));
			javalin.routes.exception(Exception.class, (e, ctx) -> writeError(ctx, 500, "INTERNAL_ERROR", "Unexpected server error", Map.of("reason", e.getMessage())));
			javalin.routes.apiBuilder(() -> {
				registerSystemRoutes(frontendAssets);
				registerApiAtConfiguredPrefix();
			});
			if (frontendAvailable) {
				javalin.routes.error(404, "text/html", ctx -> {
					if (!isApiPath(ctx.path())) serveIndex(ctx, frontendAssets);
				});
			}
		});

		app.start(config.host(), config.port());
	}

	public synchronized void stop() {
		if (app == null) return;
		app.stop();
		app = null;
	}

	public Config config() {
		return config;
	}

	private void registerSystemRoutes(FrontendAssets frontendAssets) {
		get("health", ctx -> ctx.json(Map.of("ok", true)));
		get(ctx -> serveIndex(ctx, frontendAssets));
	}

	private void registerApiAtConfiguredPrefix() {
		String[] segments = config.apiPrefix().split("/");
		registerApiAtSegment(segments, 0);
	}

	private void registerApiAtSegment(String[] segments, int index) {
		if (index >= segments.length) {
			registerApiRoutes();
			return;
		}
		String segment = segments[index];
		if (segment == null || segment.isBlank()) {
			registerApiAtSegment(segments, index + 1);
			return;
		}
		path(segment, () -> registerApiAtSegment(segments, index + 1));
	}

	private void registerApiRoutes() {
		path("auth", () -> {
			post("register", ctx -> {
				AuthResponse response = commands.register(ctx.bodyAsClass(RegisterRequest.class));
				ctx.status(201).json(response);
			});
			post("login", ctx -> ctx.json(commands.login(ctx.bodyAsClass(LoginRequest.class))));
			post("logout", ctx -> {
				commands.logout(authToken(ctx));
				ctx.status(204);
			});
			post("change-password", ctx -> {
				commands.changePassword(authToken(ctx), ctx.bodyAsClass(ChangePasswordRequest.class));
				ctx.status(204);
			});
		});

		path("users", () -> path("me", () -> {
			get(ctx -> ctx.json(commands.getMe(authToken(ctx))));
			patch(ctx -> ctx.json(commands.updateMe(authToken(ctx), ctx.bodyAsClass(UpdateUserRequest.class))));
			delete(ctx -> {
				commands.deleteMe(authToken(ctx));
				ctx.status(204);
			});
			path("credits", () -> post("purchase", ctx -> ctx.json(commands.purchaseCredits(authToken(ctx), ctx.bodyAsClass(PurchaseCreditsRequest.class)))));
		}));

		path("twins", () -> {
			get(ctx -> ctx.json(commands.listTwins(
					authToken(ctx),
					ctx.queryParam("status"),
					ctx.queryParam("type"),
					ctx.queryParam("q"),
					ctx.queryParam("sort"),
					ctx.queryParam("order")
			)));
			post(ctx -> ctx.status(201).json(commands.createTwin(authToken(ctx), ctx.bodyAsClass(CreateTwinRequest.class))));

			path("{twinId}", () -> {
				get(ctx -> ctx.json(commands.getTwin(authToken(ctx), ctx.pathParam("twinId"))));
				patch(ctx -> ctx.json(commands.updateTwin(authToken(ctx), ctx.pathParam("twinId"), bodyAsObjectMap(ctx))));
				delete(ctx -> {
					commands.deleteTwin(authToken(ctx), ctx.pathParam("twinId"));
					ctx.status(204);
				});

				path("model", () -> {
					get(ctx -> ctx.json(commands.getModel(authToken(ctx), ctx.pathParam("twinId"))));
					put(ctx -> ctx.json(commands.saveModel(authToken(ctx), ctx.pathParam("twinId"), ctx.bodyAsClass(SaveModelRequest.class))));
					post("prompt", ctx -> ctx.json(commands.applyModelPrompt(authToken(ctx), ctx.pathParam("twinId"), ctx.bodyAsClass(ApplyModelPromptRequest.class))));
				});

				path("subjects", () -> {
					get(ctx -> ctx.json(commands.listSubjects(authToken(ctx), ctx.pathParam("twinId"))));
					path("{subjectId}", () -> {
						get(ctx -> ctx.json(commands.getSubject(authToken(ctx), ctx.pathParam("twinId"), ctx.pathParam("subjectId"))));
						get("telemetry", ctx -> {
							int historyPoints = asInt(ctx.queryParam("historyPoints"), 20);
							ctx.json(commands.getSubjectTelemetry(authToken(ctx), ctx.pathParam("twinId"), ctx.pathParam("subjectId"), historyPoints));
						});
					});
				});

				path("datasets", () -> {
					get(ctx -> ctx.json(commands.listDatasets(authToken(ctx), ctx.pathParam("twinId"))));
					path("{subjectId}", () -> {
						get(ctx -> ctx.json(commands.getDataset(authToken(ctx), ctx.pathParam("twinId"), ctx.pathParam("subjectId"))));
						put(ctx -> {
							UploadedFile file = ctx.uploadedFile("file");
							if (file == null)
								throw new UiCommandException(422, "VALIDATION_ERROR", "Multipart field 'file' is required");
							byte[] content;
							try {
								content = file.content().readAllBytes();
							} catch (IOException e) {
								throw new UiCommandException(422, "INVALID_FILE", "Cannot read uploaded file");
							}
							ctx.json(commands.uploadDataset(
									authToken(ctx),
									ctx.pathParam("twinId"),
									ctx.pathParam("subjectId"),
									file.filename(),
									content
							));
						});
						delete(ctx -> {
							commands.deleteDataset(authToken(ctx), ctx.pathParam("twinId"), ctx.pathParam("subjectId"));
							ctx.status(204);
						});
					});
				});

				path("inference-engine", () -> {
					get(ctx -> ctx.json(commands.getInferenceEngine(authToken(ctx), ctx.pathParam("twinId"))));
					put(ctx -> ctx.json(commands.saveEngineConfig(authToken(ctx), ctx.pathParam("twinId"), ctx.bodyAsClass(InferenceEngine.class))));
					path("retraining", () ->
							put(ctx -> ctx.json(commands.saveRetrainingConfig(authToken(ctx), ctx.pathParam("twinId"), ctx.bodyAsClass(RetrainingConfig.class))))
					);
				});

				path("training-jobs", () -> {
					post(ctx -> ctx.status(202).json(commands.launchTraining(authToken(ctx), ctx.pathParam("twinId"))));
					path("{jobId}", () -> get(ctx -> ctx.json(commands.getTrainingJob(authToken(ctx), ctx.pathParam("twinId"), ctx.pathParam("jobId")))));
				});
			});
		});
	}

	private boolean isApiPath(String path) {
		String prefix = config.apiPrefix();
		if ("/".equals(prefix)) return true;
		return path.equals(prefix) || path.startsWith(prefix + "/");
	}

	private FrontendAssets resolveFrontendAssets() {
		if (hasBundledFrontend()) return FrontendAssets.classpath();
		Path externalWebapp = resolveExternalWebappDir();
		if (externalWebapp != null) return FrontendAssets.external(externalWebapp);
		return FrontendAssets.unavailable();
	}

	private Path resolveExternalWebappDir() {
		List<Path> candidates = List.of(
				config.workdir().resolve("webapp"),
				Path.of("res", "webapp"),
				Path.of("backend", "res", "webapp"),
				Path.of("../backend", "res", "webapp")
		);
		for (Path candidate : candidates) {
			Path normalized = candidate.toAbsolutePath().normalize();
			if (Files.isRegularFile(normalized.resolve("index.html"))) {
				return normalized;
			}
		}
		return null;
	}

	private static boolean hasBundledFrontend() {
		try (InputStream in = classpathResource(FRONTEND_INDEX_RESOURCE)) {
			return in != null;
		} catch (IOException e) {
			return false;
		}
	}

	private static void serveIndex(Context ctx, FrontendAssets frontendAssets) {
		if (frontendAssets == null || !frontendAssets.available()) {
			throw new UiCommandException(500, "FRONTEND_NOT_AVAILABLE", "Frontend entrypoint could not be served");
		}
		if (frontendAssets.location() == Location.CLASSPATH) {
			serveClasspathIndex(ctx);
			return;
		}
		serveExternalIndex(ctx, frontendAssets.indexFile());
	}

	private static void serveClasspathIndex(Context ctx) {
		try (InputStream in = classpathResource(FRONTEND_INDEX_RESOURCE)) {
			if (in == null) throw new IOException("Missing classpath resource " + FRONTEND_INDEX_RESOURCE);
			ctx.contentType("text/html; charset=utf-8");
			ctx.result(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UiCommandException(500, "FRONTEND_NOT_AVAILABLE", "Frontend entrypoint could not be served");
		}
	}

	private static void serveExternalIndex(Context ctx, Path indexFile) {
		if (indexFile == null) {
			throw new UiCommandException(500, "FRONTEND_NOT_AVAILABLE", "Frontend entrypoint could not be served");
		}
		try {
			ctx.contentType("text/html; charset=utf-8");
			ctx.result(Files.readString(indexFile, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UiCommandException(500, "FRONTEND_NOT_AVAILABLE", "Frontend entrypoint could not be served");
		}
	}

	private static InputStream classpathResource(String resourcePath) {
		return BackendWebServer.class.getClassLoader().getResourceAsStream(resourcePath);
	}

	private record FrontendAssets(Location location, String directory, Path indexFile) {
		private static FrontendAssets classpath() {
			return new FrontendAssets(Location.CLASSPATH, FRONTEND_RESOURCES_DIR, null);
		}

		private static FrontendAssets external(Path webappDir) {
			return new FrontendAssets(Location.EXTERNAL, webappDir.toString(), webappDir.resolve("index.html"));
		}

		private static FrontendAssets unavailable() {
			return new FrontendAssets(null, null, null);
		}

		private boolean available() {
			return location != null && directory != null && !directory.isBlank();
		}
	}

	public record Config(
			String host,
			int port,
			Path workdir,
			String apiPrefix
	) {
		public Config {
			host = (host == null || host.isBlank()) ? "0.0.0.0" : host.trim();
			port = port <= 0 ? 8080 : port;
			workdir = workdir == null ? Path.of("./runtime").toAbsolutePath().normalize() : workdir.toAbsolutePath().normalize();
			apiPrefix = normalizeApiPrefix(apiPrefix);
		}

		public static Config defaultConfig() {
			return new Config(
					"0.0.0.0",
					8080,
					Path.of("./runtime"),
					"/v1"
			);
		}

		private static String normalizeApiPrefix(String raw) {
			if (raw == null || raw.isBlank()) return "/v1";
			String value = raw.trim();
			if (!value.startsWith("/")) value = "/" + value;
			while (value.length() > 1 && value.endsWith("/")) {
				value = value.substring(0, value.length() - 1);
			}
			return value;
		}
	}

	private static int asInt(String raw, int fallback) {
		if (raw == null || raw.isBlank()) return fallback;
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static Map<String, Object> bodyAsObjectMap(Context ctx) {
		Map<?, ?> raw = ctx.bodyAsClass(Map.class);
		Map<String, Object> typed = new LinkedHashMap<>();
		raw.forEach((key, value) -> typed.put(String.valueOf(key), value));
		return typed;
	}

	private static String authToken(Context ctx) {
		String authorization = ctx.header("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new UiCommandException(401, "UNAUTHORIZED", "Bearer token is missing or has expired");
		}
		String token = authorization.substring("Bearer ".length()).trim();
		if (token.isBlank()) {
			throw new UiCommandException(401, "UNAUTHORIZED", "Bearer token is missing or has expired");
		}
		return token;
	}

	private static void writeError(Context ctx, int status, String code, String message, Map<String, Object> details) {
		ctx.status(status).json(new Error(code, message, details == null ? Map.of() : Map.copyOf(details)));
	}
}
