package io.picota.backend.control.ingestion;

import io.picota.backend.control.commands.UiCommandSet;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

public final class IngestionApiRoutes {
	private final UiCommandSet commands;

	public IngestionApiRoutes(UiCommandSet commands) {
		this.commands = commands;
	}

	public void register() {
		path("ingestion", () -> path("v1", () -> path("twins", () -> path("{twinId}", () -> path("subjects", () -> path("{subjectId}", () -> path("metrics", () -> {
			post("sensors", ctx -> {
				commands.ingestSubjectSensorMetrics(
						authToken(ctx),
						ctx.pathParam("twinId"),
						ctx.pathParam("subjectId"),
						ctx.bodyAsClass(IngestMetricsRequest.class)
				);
				ctx.status(202);
			});
		})))))));
	}

	private static String authToken(io.javalin.http.Context ctx) {
		String auth = ctx.header("Authorization");
		if (auth == null || auth.isBlank()) return null;
		return auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
	}
}
