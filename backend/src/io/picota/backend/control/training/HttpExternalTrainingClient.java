package io.picota.backend.control.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpExternalTrainingClient implements ExternalTrainingClient {
	private static final String TRAININGS_PATH = "/api/v1/trainings";
	private static final String INFERENCES_PATH = "/api/v1/inferences";

	private final String baseUrl;
	private final Duration requestTimeout;
	private final HttpClient client;
	private final ObjectMapper mapper;

	public HttpExternalTrainingClient(String baseUrl, Duration requestTimeout) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("Training API base URL is required");
		}
		this.baseUrl = stripTrailingSlash(baseUrl.trim());
		this.requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
				? Duration.ofSeconds(30)
				: requestTimeout;
		this.client = HttpClient.newBuilder()
				.connectTimeout(this.requestTimeout)
				.build();
		this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
	}

	@Override
	public TrainingTicketAccepted createTraining(Map<String, Object> request) {
		String body = writeJson(request == null ? Map.of() : request);
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + TRAININGS_PATH))
				.timeout(requestTimeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		String response = send(httpRequest, 202);
		JsonNode json = readJson(response);
		return new TrainingTicketAccepted(
				text(json, "ticket_id", ""),
				text(json, "status", "queued"),
				parseInstant(text(json, "created_at", ""))
		);
	}

	@Override
	public TrainingTicketSnapshot getTraining(String ticketId) {
		if (ticketId == null || ticketId.isBlank()) {
			throw new TrainingApiException("Ticket id is required");
		}
		String encoded = URLEncoder.encode(ticketId.trim(), StandardCharsets.UTF_8);
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + TRAININGS_PATH + "/" + encoded))
				.timeout(requestTimeout)
				.GET()
				.build();
		String response = send(httpRequest, 200);
		JsonNode json = readJson(response);
		JsonNode progress = json.path("progress");
		JsonNode result = json.path("result");
		JsonNode testMetrics = result.path("test_metrics");
		JsonNode testViolationReport = result.path("test_violation_report");
		if (!testMetrics.isObject()) {
			testMetrics = result.path("val_metrics");
		}
		if (!testViolationReport.isObject()) {
			testViolationReport = result.path("val_violation_report");
		}
		Double testElapsedSeconds = nullableDouble(result, "test_elapsed_seconds");
		if (testElapsedSeconds == null) {
			JsonNode metadata = result.path("metadata");
			testElapsedSeconds = nullableDouble(metadata, "test_elapsed_seconds");
			if (testElapsedSeconds == null) {
				testElapsedSeconds = nullableDouble(metadata, "test_seconds");
			}
		}
		Integer epochsCompleted = nullableInt(progress, "epochs_completed");
		Integer epochsTotal = nullableInt(progress, "epochs_total");
		if (epochsCompleted == null) epochsCompleted = nullableInt(json, "epochs_completed");
		if (epochsTotal == null) epochsTotal = nullableInt(json, "epochs_total");
		Double progressPercent = nullableDouble(progress, "percent");
		if (progressPercent == null && epochsCompleted != null && epochsTotal != null && epochsTotal > 0) {
			progressPercent = (epochsCompleted * 100.0) / epochsTotal;
		}
		Map<String, Double> constraintViolationRates = violationRatesByConstraint(testViolationReport.path("by_test"));
		TrainingTicketOutcome outcome = result.isObject()
				? new TrainingTicketOutcome(
				text(result, "output_variable", ""),
				inputVariablesFromResult(result),
				nullableDouble(testMetrics, "r2"),
				nullableDouble(testMetrics, "mae_raw"),
				nullableInt(testMetrics, "n_samples"),
				testElapsedSeconds,
				nullableDouble(testMetrics, "accuracy"),
				nullableDouble(testMetrics, "macro_f1"),
				nullableDouble(testViolationReport, "overall_violation_rate"),
				constraintViolationRates
		)
				: null;
		String errorMessage = json.path("error").isObject() ? text(json.path("error"), "message", "") : "";
		List<String> historyStatuses = new ArrayList<>();
		JsonNode history = json.path("history");
		if (history.isArray()) {
			for (JsonNode item : history) {
				String status = text(item, "status", "");
				if (!status.isBlank()) historyStatuses.add(status);
			}
		}
		return new TrainingTicketSnapshot(
				text(json, "ticket_id", ticketId),
				text(json, "status", "queued"),
				parseInstant(text(json, "created_at", "")),
				parseInstant(text(json, "updated_at", "")),
				parseInstant(text(json, "started_at", "")),
				parseInstant(text(json, "finished_at", "")),
				epochsCompleted,
				epochsTotal,
				progressPercent,
				outcome,
				errorMessage,
				historyStatuses
		);
	}

	@Override
	public TrainingInferenceResult createInference(Map<String, Object> request) {
		String body = writeJson(request == null ? Map.of() : request);
		HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + INFERENCES_PATH))
				.timeout(requestTimeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		String response = send(httpRequest, 200);
		JsonNode json = readJson(response);
		JsonNode firstPrediction = null;
		JsonNode predictions = json.path("predictions");
		if (predictions.isArray() && predictions.size() > 0) {
			firstPrediction = predictions.get(0);
		}
		Double predictedValue = null;
		if (firstPrediction != null && firstPrediction.isObject()) {
			predictedValue = nullableDouble(firstPrediction, "value");
			if (predictedValue == null) predictedValue = nullableDouble(firstPrediction, "raw");
			if (predictedValue == null) predictedValue = nullableDouble(firstPrediction, "normalized");
		}
		return new TrainingInferenceResult(
				text(json, "output_variable", ""),
				predictedValue,
				parseInstant(text(json, "inferred_at", ""))
		);
	}

	private String send(HttpRequest request, int expectedStatusCode) {
		HttpResponse<String> response;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TrainingApiException("Unable to reach training API", e);
		} catch (IOException e) {
			throw new TrainingApiException("Unable to reach training API", e);
		}
		int status = response.statusCode();
		String body = response.body() == null ? "" : response.body();
		if (status != expectedStatusCode) {
			throw new TrainingApiException("Unexpected training API response: HTTP " + status, status, body);
		}
		return body;
	}

	private String writeJson(Object payload) {
		try {
			return mapper.writeValueAsString(payload);
		} catch (IOException e) {
			throw new TrainingApiException("Unable to serialize training request payload", e);
		}
	}

	private JsonNode readJson(String raw) {
		if (raw == null || raw.isBlank()) return mapper.createObjectNode();
		try {
			return mapper.readTree(raw);
		} catch (IOException e) {
			throw new TrainingApiException("Unable to parse training API response", e);
		}
	}

	private static String text(JsonNode node, String field, String fallback) {
		if (node == null || !node.has(field) || node.get(field).isNull()) return fallback;
		String value = node.get(field).asText();
		return value == null || value.isBlank() ? fallback : value;
	}

	private static Double nullableDouble(JsonNode node, String field) {
		if (node == null || !node.has(field) || node.get(field).isNull()) return null;
		JsonNode value = node.get(field);
		if (value.isNumber()) return value.doubleValue();
		try {
			return Double.parseDouble(value.asText());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Integer nullableInt(JsonNode node, String field) {
		if (node == null || !node.has(field) || node.get(field).isNull()) return null;
		JsonNode value = node.get(field);
		if (value.isInt() || value.isLong()) return value.intValue();
		try {
			return Integer.parseInt(value.asText());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Map<String, Double> violationRatesByConstraint(JsonNode byTestNode) {
		if (byTestNode == null || !byTestNode.isObject()) return Map.of();
		Map<String, Double> rates = new LinkedHashMap<>();
		byTestNode.fields().forEachRemaining(entry -> {
			String constraint = entry.getKey();
			if (constraint == null || constraint.isBlank()) return;
			JsonNode stats = entry.getValue();
			Double rate = nullableDouble(stats, "violation_rate");
			if (rate == null) return;
			rates.put(constraint, rate);
		});
		return rates;
	}

	private static List<String> textList(JsonNode node) {
		if (node == null || !node.isArray()) return List.of();
		List<String> result = new ArrayList<>();
		for (JsonNode item : node) {
			if (item == null || item.isNull()) continue;
			String value = item.asText();
			if (value == null || value.isBlank()) continue;
			result.add(value.trim());
		}
		return List.copyOf(result);
	}

	private static List<String> inputVariablesFromResult(JsonNode resultNode) {
		if (resultNode == null || !resultNode.isObject()) return List.of();
		List<String> explicit = textList(resultNode.path("input_variables"));
		if (!explicit.isEmpty()) return explicit;

		JsonNode featureNames = resultNode.path("metadata").path("feature_names");
		if (!featureNames.isObject()) return List.of();
		List<String> values = new ArrayList<>();
		values.addAll(textList(featureNames.path("t")));
		values.addAll(textList(featureNames.path("numerical_t_features")));
		values.addAll(textList(featureNames.path("categorical_t_features")));
		values.addAll(textList(featureNames.path("lookback_t")));
		values.addAll(textList(featureNames.path("numerical_lookback_features")));
		values.addAll(textList(featureNames.path("categorical_lookback_features")));
		return values.isEmpty() ? List.of() : List.copyOf(values);
	}

	private static Instant parseInstant(String raw) {
		if (raw == null || raw.isBlank()) return null;
		try {
			return Instant.parse(raw);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String stripTrailingSlash(String value) {
		String normalized = value;
		while (normalized.endsWith("/") && normalized.length() > 1) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}
}
