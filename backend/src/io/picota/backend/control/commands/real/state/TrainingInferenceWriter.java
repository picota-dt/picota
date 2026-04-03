package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.training.*;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.persistence.DatasetStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrainingInferenceWriter {
	private static final Pattern HORIZON_PATTERN = Pattern.compile("(?i)\\bt\\s*\\+\\s*(\\d+)\\b");

	private final DatasetStorage datasetStorage;
	private final ExternalTrainingClient trainingClient;
	private final ConcurrentMap<String, PredictionTemporalContext> predictionTemporalByTicketId = new ConcurrentHashMap<>();

	TrainingInferenceWriter(DatasetStorage datasetStorage, ExternalTrainingClient trainingClient) {
		this.datasetStorage = datasetStorage == null ? DatasetStorage.noOp() : datasetStorage;
		this.trainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
	}

	void rememberTemporalContext(
			String ticketId,
			String subjectId,
			String outputVariable,
			TimeBucket timeBucket,
			Integer predictionTimeHorizon,
			Integer requestTimeHorizon
	) {
		if (ticketId == null || ticketId.isBlank()) return;
		predictionTemporalByTicketId.put(
				ticketId.trim(),
				new PredictionTemporalContext(
						subjectId,
						outputVariable,
						timeBucket,
						predictionTimeHorizon,
						requestTimeHorizon
				)
		);
	}

	void appendInferencePredictionSample(DigitalTwin twin, TrainingTicketSnapshot snapshot) {
		appendInferencePredictionSampleForSubject(twin, snapshot, null);
	}

	void appendInferencePredictionSampleForSubject(DigitalTwin twin, TrainingTicketSnapshot snapshot, String subjectId) {
		if (twin == null || snapshot == null) return;
		String ticketId = snapshot.ticketId();
		if (ticketId == null || ticketId.isBlank()) return;
		PredictionTemporalContext expectedContext = predictionTemporalByTicketId.get(ticketId.trim());
		Optional<InferenceTarget> target = resolveInferenceTarget(twin, snapshot.outcome(), subjectId, expectedContext);
		if (target.isEmpty()) return;
		DigitalSubject subject = target.get().subject();
		Optional<Path> datasetPath = datasetStorage.resolveDatasetPath(twin.id(), subject.id(), subject.name());
		if (datasetPath.isEmpty()) return;
		Optional<LatestDatasetRow> latestDatasetRow = readLatestDatasetRow(datasetPath.get());
		if (latestDatasetRow.isEmpty()) return;
		LatestSensorValues latestSensorValues = readLatestSensorValues(twin, subject);
		Instant baseInstant = resolveInferenceBaseInstant(latestDatasetRow.get(), latestSensorValues);
		Map<String, Double> variables = buildInferenceVariables(snapshot.outcome(), latestDatasetRow.get(), latestSensorValues, baseInstant);
		if (variables.isEmpty()) return;

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("training_ticket_id", ticketId.trim());
		request.put("output_scale", "raw");
		request.put("instances", List.of(Map.of("variables", variables)));
		TrainingInferenceResult inferenceResult = requestInferenceWithRetries(request);
		if (inferenceResult == null || inferenceResult.prediction() == null || !Double.isFinite(inferenceResult.prediction())) {
			return;
		}

		Variable inferredVariable = target.get().variable();
		PredictionTemporalContext predictionContext = resolvePredictionTemporalContext(ticketId, subject, inferredVariable);
		TimeBucket timeBucket = predictionContext != null ? predictionContext.timeBucket() : subject.timeBucket();
		Integer timeHorizon = firstNonNull(
				predictionContext == null ? null : predictionContext.predictionTimeHorizon(),
				inferredVariable.timeHorizon(),
				predictionContext == null ? null : predictionContext.requestTimeHorizon()
		);
		if (timeHorizon == null) {
			timeHorizon = parseTimeHorizon(snapshot.outcome() == null ? null : snapshot.outcome().outputVariable());
		}
		Instant predictedInstant = resolvePredictedInstant(baseInstant, timeBucket, timeHorizon);
		String inferredStorageVariableId = inferredStorageVariableId(inferredVariable, timeHorizon);
		datasetStorage.appendMetric(
				twin.id(),
				twin.version(),
				subject.id(),
				subject.name(),
				inferredStorageVariableId,
				inferredVariable.name(),
				DatasetStorage.MetricKind.INFERRED,
				predictedInstant,
				inferenceResult.prediction()
		);
	}

	boolean hasInferenceTarget(DigitalTwin twin, TrainingTicketOutcome outcome, String subjectId) {
		return resolveInferenceTarget(twin, outcome, subjectId, null).isPresent();
	}

	private TrainingInferenceResult requestInferenceWithRetries(Map<String, Object> request) {
		final int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return trainingClient.createInference(request);
			} catch (TrainingApiException exception) {
				if (!shouldRetryInferenceCall(exception, attempt, maxAttempts)) {
					return null;
				}
				sleepSilently(250L * attempt);
			} catch (RuntimeException ignored) {
				return null;
			}
		}
		return null;
	}

	private static boolean shouldRetryInferenceCall(TrainingApiException exception, int attempt, int maxAttempts) {
		if (exception == null || attempt >= maxAttempts) return false;
		int status = exception.statusCode();
		return status == 404 || status == 409 || status == 429 || status >= 500;
	}

	private static void sleepSilently(long millis) {
		if (millis <= 0) return;
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	private Optional<InferenceTarget> resolveInferenceTarget(
			DigitalTwin twin,
			TrainingTicketOutcome outcome,
			String subjectIdFilter,
			PredictionTemporalContext expectedContext
	) {
		if (twin == null || outcome == null) return Optional.empty();
		String outputVariable = outcome.outputVariable();
		if (outputVariable == null || outputVariable.isBlank()) return Optional.empty();
		List<InferenceTarget> candidates = new ArrayList<>();
		for (DigitalSubject subject : twin.subjects()) {
			if (subject == null || subject.variables() == null) continue;
			if (subjectIdFilter != null && !subjectIdFilter.isBlank() && !subjectIdFilter.equals(subject.id()))
				continue;
			for (Variable variable : subject.variables()) {
				if (variable == null || variable.variableType() != VariableType.INFERRED) continue;
				if (!matchesOutputVariable(variable, outputVariable)) continue;
				candidates.add(new InferenceTarget(subject, variable));
			}
		}
		if (candidates.isEmpty()) return Optional.empty();
		if (expectedContext == null) return Optional.of(candidates.get(0));
		Optional<InferenceTarget> exact = candidates.stream()
				.filter(target -> contextMatches(expectedContext, target))
				.findFirst();
		return exact.isPresent() ? exact : Optional.of(candidates.get(0));
	}

	private static boolean contextMatches(PredictionTemporalContext context, InferenceTarget target) {
		if (context == null || target == null || target.subject() == null || target.variable() == null) return false;
		if (context.subjectId() != null && target.subject().id() != null
				&& !context.subjectId().equalsIgnoreCase(target.subject().id())) return false;
		if (context.outputVariable() != null && !matchesOutputVariable(target.variable(), context.outputVariable()))
			return false;
		if (context.predictionTimeHorizon() != null) {
			Integer variableHorizon = target.variable().timeHorizon();
			if (!Objects.equals(variableHorizon, context.predictionTimeHorizon())) return false;
		}
		return true;
	}

	private Map<String, Double> buildInferenceVariables(
			TrainingTicketOutcome outcome,
			LatestDatasetRow row,
			LatestSensorValues sensorValues,
			Instant baseInstant
	) {
		if (outcome == null || row == null) return Map.of();
		List<String> inputVariables = outcome.inputVariables();
		if (inputVariables == null || inputVariables.isEmpty()) return Map.of();
		Map<String, String> rowValues = row.values();
		ZonedDateTime timestamp = (baseInstant == null ? Instant.now() : baseInstant).atZone(ZoneOffset.UTC);
		Map<String, Double> payload = new LinkedHashMap<>();
		for (String featureName : inputVariables) {
			if (featureName == null || featureName.isBlank()) continue;
			String feature = featureName.trim();
			Double timeFeature = timeFeatureValue(feature, timestamp);
			if (timeFeature != null) {
				payload.put(feature, timeFeature);
				continue;
			}
			int oneHotSeparator = feature.indexOf('=');
			if (oneHotSeparator > 0) {
				String column = feature.substring(0, oneHotSeparator);
				String category = feature.substring(oneHotSeparator + 1);
				String currentValue = resolveColumnValue(rowValues, column);
				payload.put(feature, Objects.equals(currentValue == null ? "" : currentValue, category) ? 1.0 : 0.0);
				continue;
			}
			Double numericValue = sensorFeatureValue(sensorValues, feature);
			if (numericValue == null) {
				numericValue = parseNumeric(resolveColumnValue(rowValues, feature));
			}
			payload.put(feature, numericValue == null ? 0.0 : numericValue);
		}
		return payload;
	}

	private PredictionTemporalContext resolvePredictionTemporalContext(String ticketId, DigitalSubject subject, Variable variable) {
		if (ticketId == null || ticketId.isBlank() || subject == null || variable == null) return null;
		PredictionTemporalContext context = predictionTemporalByTicketId.get(ticketId.trim());
		if (context == null) return null;
		if (context.subjectId() != null && subject.id() != null && !context.subjectId().equalsIgnoreCase(subject.id()))
			return null;
		if (context.outputVariable() != null && !matchesOutputVariable(variable, context.outputVariable())) return null;
		return context;
	}

	private static Integer parseTimeHorizon(String outputVariableName) {
		if (outputVariableName == null || outputVariableName.isBlank()) return null;
		Matcher matcher = HORIZON_PATTERN.matcher(outputVariableName.trim());
		if (!matcher.find()) return null;
		try {
			int parsed = Integer.parseInt(matcher.group(1));
			return parsed > 0 ? parsed : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Instant resolveInferenceBaseInstant(LatestDatasetRow datasetRow, LatestSensorValues sensorValues) {
		Instant sensorInstant = sensorValues == null ? null : sensorValues.instant();
		if (sensorInstant != null) return sensorInstant;
		Instant rowInstant = datasetRow == null ? null : datasetRow.instant();
		return rowInstant == null ? Instant.now() : rowInstant;
	}

	private static Instant resolvePredictedInstant(Instant baseInstant, TimeBucket timeBucket, Integer timeHorizon) {
		Instant safeBase = baseInstant == null ? Instant.now() : baseInstant;
		if (timeHorizon == null || timeHorizon <= 0 || timeBucket == null) return safeBase;
		try {
			ZonedDateTime reference = safeBase.atZone(ZoneOffset.UTC);
			return switch (timeBucket) {
				case YEARS -> reference.plusYears(timeHorizon.longValue()).toInstant();
				case MONTHS -> reference.plusMonths(timeHorizon.longValue()).toInstant();
				case DAYS -> reference.plusDays(timeHorizon.longValue()).toInstant();
				case HOURS -> reference.plusHours(timeHorizon.longValue()).toInstant();
				case MINUTES -> reference.plusMinutes(timeHorizon.longValue()).toInstant();
				case SECONDS -> reference.plusSeconds(timeHorizon.longValue()).toInstant();
			};
		} catch (RuntimeException ignored) {
			return safeBase;
		}
	}

	private LatestSensorValues readLatestSensorValues(DigitalTwin twin, DigitalSubject subject) {
		if (twin == null || subject == null || subject.variables() == null) return LatestSensorValues.empty();
		Map<String, Double> valuesByKey = new LinkedHashMap<>();
		Instant latestInstant = null;
		for (Variable variable : subject.variables()) {
			if (variable == null || variable.variableType() != VariableType.SENSOR) continue;
			DatasetStorage.MetricSeries series = datasetStorage.readMetricSeries(
					twin.id(),
					twin.version(),
					subject.id(),
					subject.name(),
					variable.id(),
					variable.name(),
					DatasetStorage.MetricKind.SENSORS,
					1
			);
			DatasetStorage.MetricSample latest = series.latest();
			if (latest == null || latest.value() == null || !Double.isFinite(latest.value())) continue;
			putSensorFeatureValue(valuesByKey, variable.id(), latest.value());
			putSensorFeatureValue(valuesByKey, variable.name(), latest.value());
			if (latest.instant() != null && (latestInstant == null || latest.instant().isAfter(latestInstant))) {
				latestInstant = latest.instant();
			}
		}
		return new LatestSensorValues(valuesByKey, latestInstant);
	}

	private static void putSensorFeatureValue(Map<String, Double> valuesByKey, String key, Double value) {
		if (valuesByKey == null || value == null || !Double.isFinite(value)) return;
		String normalized = normalizeKey(key);
		if (normalized == null) return;
		valuesByKey.put(normalized, value);
	}

	private static Double sensorFeatureValue(LatestSensorValues sensorValues, String featureName) {
		if (sensorValues == null || sensorValues.valuesByKey() == null || sensorValues.valuesByKey().isEmpty())
			return null;
		String normalized = normalizeKey(featureName);
		if (normalized == null) return null;
		return sensorValues.valuesByKey().get(normalized);
	}

	private static String normalizeKey(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private Optional<LatestDatasetRow> readLatestDatasetRow(Path datasetPath) {
		if (datasetPath == null) return Optional.empty();
		List<String> lines;
		try {
			lines = Files.readAllLines(datasetPath, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			return Optional.empty();
		}
		if (lines.size() < 2) return Optional.empty();
		char delimiter = detectDelimiter(datasetPath, lines);
		List<String> headers = parseDelimitedLine(lines.get(0), delimiter);
		if (headers.isEmpty()) return Optional.empty();
		String latestLine = null;
		for (int i = lines.size() - 1; i >= 1; i--) {
			String candidate = lines.get(i);
			if (candidate == null || candidate.isBlank()) continue;
			latestLine = candidate;
			break;
		}
		if (latestLine == null) return Optional.empty();
		List<String> cells = parseDelimitedLine(latestLine, delimiter);
		Map<String, String> row = new LinkedHashMap<>();
		for (int col = 0; col < headers.size(); col++) {
			String header = headers.get(col);
			if (header == null || header.isBlank()) continue;
			String value = col < cells.size() ? cells.get(col) : "";
			row.put(header.trim(), value == null ? "" : value.trim());
		}
		Instant instant = parseInstantLike(resolveColumnValue(row, "instant"));
		return Optional.of(new LatestDatasetRow(instant == null ? Instant.now() : instant, Map.copyOf(row)));
	}

	private static char detectDelimiter(Path datasetPath, List<String> lines) {
		String fileName = datasetPath.getFileName() == null ? "" : datasetPath.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.endsWith(".tsv")) return '\t';
		for (String line : lines) {
			if (line == null || line.isBlank()) continue;
			if (line.contains("\t")) return '\t';
			if (line.contains(";")) return ';';
			break;
		}
		return ',';
	}

	private static List<String> parseDelimitedLine(String line, char delimiter) {
		if (line == null) return List.of();
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
				continue;
			}
			if (c == delimiter && !inQuotes) {
				values.add(current.toString().trim());
				current.setLength(0);
				continue;
			}
			current.append(c);
		}
		values.add(current.toString().trim());
		return values;
	}

	private static Instant parseInstantLike(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) return null;
		String candidate = rawValue.trim();
		try {
			return Instant.parse(candidate);
		} catch (Exception ignored) {
		}
		try {
			return ZonedDateTime.parse(candidate).toInstant();
		} catch (Exception ignored) {
		}
		try {
			return LocalDateTime.parse(candidate).atZone(ZoneOffset.UTC).toInstant();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String resolveColumnValue(Map<String, String> rowValues, String key) {
		if (rowValues == null || rowValues.isEmpty() || key == null || key.isBlank()) return null;
		if (rowValues.containsKey(key)) return rowValues.get(key);
		for (Map.Entry<String, String> entry : rowValues.entrySet()) {
			if (entry.getKey() == null) continue;
			if (entry.getKey().trim().equalsIgnoreCase(key.trim())) return entry.getValue();
		}
		return null;
	}

	private static Double parseNumeric(String rawValue) {
		if (rawValue == null || rawValue.isBlank()) return null;
		try {
			double parsed = Double.parseDouble(rawValue.trim());
			return Double.isFinite(parsed) ? parsed : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Double timeFeatureValue(String featureName, ZonedDateTime timestamp) {
		if (featureName == null || timestamp == null) return null;
		double monthIndex = timestamp.getMonthValue() - 1.0;
		double dayIndex = timestamp.getDayOfMonth() - 1.0;
		double hourIndex = timestamp.getHour();
		double quarterIndex = (timestamp.getMonthValue() - 1) / 3.0;
		double weekIndex = timestamp.get(WeekFields.ISO.weekOfWeekBasedYear()) - 1.0;
		return switch (featureName) {
			case "month_sin" -> sinComponent(monthIndex, 12.0);
			case "month_cos" -> cosComponent(monthIndex, 12.0);
			case "day_sin" -> sinComponent(dayIndex, 31.0);
			case "day_cos" -> cosComponent(dayIndex, 31.0);
			case "hour_sin" -> sinComponent(hourIndex, 24.0);
			case "hour_cos" -> cosComponent(hourIndex, 24.0);
			case "week_sin" -> sinComponent(weekIndex, 53.0);
			case "week_cos" -> cosComponent(weekIndex, 53.0);
			case "quarter_sin" -> sinComponent(quarterIndex, 4.0);
			case "quarter_cos" -> cosComponent(quarterIndex, 4.0);
			default -> null;
		};
	}

	private static double sinComponent(double value, double period) {
		return Math.sin((2.0 * Math.PI * value) / period);
	}

	private static double cosComponent(double value, double period) {
		return Math.cos((2.0 * Math.PI * value) / period);
	}

	private static String columnName(Variable variable) {
		if (variable == null) return "variable";
		if (variable.name() != null && !variable.name().isBlank()) return variable.name().trim();
		if (variable.id() != null && !variable.id().isBlank()) return variable.id().trim();
		return "variable";
	}

	private static String inferredStorageVariableId(Variable variable, Integer resolvedTimeHorizon) {
		String base = trimToNull(variable == null ? null : variable.id());
		if (base == null) base = trimToNull(variable == null ? null : variable.name());
		if (base == null) base = "variable";
		String lowerBase = base.toLowerCase(Locale.ROOT);
		if (lowerBase.contains("__t_plus_") || lowerBase.endsWith("__inferred")) return base;
		if (resolvedTimeHorizon != null && resolvedTimeHorizon > 0) {
			return base + "__t_plus_" + resolvedTimeHorizon;
		}
		return base + "__inferred";
	}

	private static boolean matchesOutputVariable(Variable variable, String output) {
		String normalizedOutput = output.trim();
		if (normalizedOutput.isBlank()) return false;
		if (variable.name() != null && variable.name().trim().equalsIgnoreCase(normalizedOutput)) return true;
		return variable.id() != null && variable.id().trim().equalsIgnoreCase(normalizedOutput);
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		if (values == null) return null;
		for (T value : values) {
			if (value != null) return value;
		}
		return null;
	}

	private record PredictionTemporalContext(
			String subjectId,
			String outputVariable,
			TimeBucket timeBucket,
			Integer predictionTimeHorizon,
			Integer requestTimeHorizon
	) {
	}

	private record InferenceTarget(
			DigitalSubject subject,
			Variable variable
	) {
	}

	private record LatestDatasetRow(
			Instant instant,
			Map<String, String> values
	) {
		public LatestDatasetRow {
			values = values == null ? Map.of() : Map.copyOf(values);
		}
	}

	private record LatestSensorValues(
			Map<String, Double> valuesByKey,
			Instant instant
	) {
		public LatestSensorValues {
			valuesByKey = valuesByKey == null ? Map.of() : Map.copyOf(valuesByKey);
		}

		private static LatestSensorValues empty() {
			return new LatestSensorValues(Map.of(), null);
		}
	}
}
