package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.training.*;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.persistence.DatasetStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TrainingOperationsDelegate {
	private static final int DEFAULT_EPOCHS = 50;
	private static final double DEFAULT_LEARNING_RATE = 0.0005;
	private static final int DEFAULT_WINDOW_SIZE = 0;
	private static final int DEFAULT_BATCH_SIZE = 64;
	private static final int MAX_UPSTREAM_BODY_LENGTH = 3_000;

	private final ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser;
	private final ConcurrentMap<String, TrainingJob> trainingJobs;
	private final ConcurrentMap<String, String> trainingJobOwnerById;
	private final DatasetStorage datasetStorage;
	private final ExternalTrainingClient trainingClient;
	private final TrainingDatasetPreparer trainingDatasetPreparer;
	private final Runnable persistAction;

	public TrainingOperationsDelegate(
			ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser,
			ConcurrentMap<String, TrainingJob> trainingJobs,
			ConcurrentMap<String, String> trainingJobOwnerById,
			DatasetStorage datasetStorage,
			ExternalTrainingClient trainingClient,
			Runnable persistAction
	) {
		this.twinsByUser = twinsByUser;
		this.trainingJobs = trainingJobs;
		this.trainingJobOwnerById = trainingJobOwnerById;
		this.datasetStorage = datasetStorage == null ? DatasetStorage.noOp() : datasetStorage;
		this.trainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
		this.trainingDatasetPreparer = new TrainingDatasetPreparer(this.datasetStorage);
		this.persistAction = persistAction;
	}

	public InferenceEngine getInferenceEngine(DigitalTwin twin) {
		return twin.inferenceEngine() == null ? null : UiCommandFixtures.copyInferenceEngine(twin.inferenceEngine());
	}

	public InferenceEngine saveEngineConfig(String userId, DigitalTwin twin, InferenceEngine request) {
		InferenceEngine current = twin.inferenceEngine();
		InferenceEngine next = new InferenceEngine(
				request != null && request.trained() != null
						? request.trained()
						: current != null && current.trained(),
				request != null && request.algorithm() != null
						? request.algorithm()
						: current != null && current.algorithm() != null ? current.algorithm() : TrainingAlgorithm.KAN,
				request != null ? request.trainedAt() : current == null ? null : current.trainedAt(),
				request != null && request.launchedAt() != null
						? request.launchedAt()
						: current == null ? null : current.launchedAt(),
				request != null && request.trainingDurationSeconds() != null
						? request.trainingDurationSeconds()
						: current == null ? null : current.trainingDurationSeconds(),
				request != null && request.epochs() != null
						? request.epochs()
						: current == null ? DEFAULT_EPOCHS : current.epochs(),
				request != null && request.learningRate() != null
						? request.learningRate()
						: current == null ? DEFAULT_LEARNING_RATE : current.learningRate(),
				request != null && request.windowSize() != null
						? request.windowSize()
						: current == null ? DEFAULT_WINDOW_SIZE : current.windowSize(),
				request != null && request.batchSize() != null
						? request.batchSize()
						: current == null ? DEFAULT_BATCH_SIZE : current.batchSize(),
				request != null && request.inferredVariables() != null
						? request.inferredVariables()
						: current == null ? List.of() : current.inferredVariables(),
				request != null && request.retrainingConfig() != null
						? request.retrainingConfig()
						: current == null ? null : current.retrainingConfig()
		);
		DigitalTwin updatedTwin = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				next,
				twin.datasets(),
				twin.ingestionToken()
		);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), updatedTwin);
		persistAction.run();
		return UiCommandFixtures.copyInferenceEngine(next);
	}

	public RetrainingConfig saveRetrainingConfig(String userId, DigitalTwin twin, RetrainingConfig request) {
		InferenceEngine current = twin.inferenceEngine() == null
				? new InferenceEngine(
				false,
				TrainingAlgorithm.KAN,
				null,
				null,
				null,
				DEFAULT_EPOCHS,
				DEFAULT_LEARNING_RATE,
				DEFAULT_WINDOW_SIZE,
				DEFAULT_BATCH_SIZE,
				List.of(),
				null
		)
				: twin.inferenceEngine();
		RetrainingConfig retraining = request == null
				? new RetrainingConfig(false, RetrainingSchedule.WEEKLY, 500, "02:00")
				: request;
		InferenceEngine updatedEngine = new InferenceEngine(
				current.trained(),
				current.algorithm(),
				current.trainedAt(),
				current.launchedAt(),
				current.trainingDurationSeconds(),
				current.epochs(),
				current.learningRate(),
				current.windowSize(),
				current.batchSize(),
				current.inferredVariables(),
				retraining
		);
		DigitalTwin updatedTwin = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				updatedEngine,
				twin.datasets(),
				twin.ingestionToken()
		);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), updatedTwin);
		persistAction.run();
		return retraining;
	}

	public TrainingJob launchTraining(String userId, String twinId, DigitalTwin twin) {
		validate(twin.inferenceEngine() != null, 422, "PRECONDITION_FAILED", "Inference engine is not configured");
		TrainingLaunchContext launchContext = resolveLaunchContext(twin);

		boolean hasRunning = trainingJobs.values().stream()
				.anyMatch(job -> job.twinId().equals(twinId) &&
						(job.status() == TrainingJobStatus.QUEUED
								|| job.status() == TrainingJobStatus.PREPARING
								|| job.status() == TrainingJobStatus.TRAINING
								|| job.status() == TrainingJobStatus.EVALUATING));
		if (hasRunning) {
			throw new UiCommandException(409, "TRAINING_ALREADY_RUNNING", "A training job is already in progress for this twin");
		}

		Map<String, Object> request = buildTrainingRequest(userId, twin, launchContext);
		TrainingTicketAccepted accepted = createTrainingTicket(request);
		String jobId = normalizeTicketId(accepted.ticketId());
		TrainingJobStatus initialStatus = mapExternalStatus(accepted.status(), null);
		TrainingJob job = new TrainingJob(
				jobId,
				twinId,
				initialStatus,
				resolveProgress(initialStatus, null, null),
				phaseForStatus(initialStatus),
				accepted.createdAt() == null ? Instant.now() : accepted.createdAt(),
				null,
				null,
				null,
				null
		);
		trainingJobs.put(job.jobId(), job);
		trainingJobOwnerById.put(job.jobId(), userId);
		persistAction.run();
		return job;
	}

	public TrainingJob getTrainingJob(String userId, String twinId, String jobId) {
		TrainingJob current = requireTrainingJob(userId, twinId, jobId);
		if (current.status() != null && isTerminal(current.status())) {
			return current;
		}
		TrainingTicketSnapshot snapshot = getTrainingTicket(jobId);
		TrainingJobStatus nextStatus = mapExternalStatus(snapshot.status(), current);
		if (nextStatus == TrainingJobStatus.TRAINING && snapshot.outcome() != null) {
			nextStatus = TrainingJobStatus.EVALUATING;
		}
		Integer nextProgress = resolveProgress(nextStatus, current.progress(), snapshot);
		Instant startedAt = selectStartedAt(current, snapshot, nextStatus);
		Instant completedAt = isTerminal(nextStatus) ? selectCompletedAt(current, snapshot) : null;
		String errorMessage = nextStatus == TrainingJobStatus.FAILED
				? nonBlank(snapshot.errorMessage()).orElse("Training failed")
				: null;

		InferenceEngine result = current.result();
		if (nextStatus == TrainingJobStatus.DONE) {
			result = finalizeTraining(userId, twinId, snapshot);
		}

		TrainingJob updated = new TrainingJob(
				current.jobId(),
				current.twinId(),
				nextStatus,
				nextProgress,
				phaseForSnapshot(nextStatus, snapshot),
				current.createdAt(),
				startedAt,
				completedAt,
				errorMessage,
				result
		);
		trainingJobs.put(updated.jobId(), updated);
		persistAction.run();
		return updated;
	}

	public void inferSubjectFromLatestCompletedTraining(String userId, DigitalTwin twin, String subjectId) {
		if (userId == null || userId.isBlank() || twin == null || subjectId == null || subjectId.isBlank()) return;
		try {
			Optional<TrainingTicketSnapshot> snapshot = resolveLatestCompletedSnapshotForSubject(userId, twin, subjectId);
			if (snapshot.isEmpty()) return;
			appendInferencePredictionSampleForSubject(twin, snapshot.get(), subjectId);
		} catch (RuntimeException ignored) {
			// Never fail ingestion because inference side-effect failed.
		}
	}

	private Optional<TrainingTicketSnapshot> resolveLatestCompletedSnapshotForSubject(String userId, DigitalTwin twin, String subjectId) {
		List<TrainingJob> candidates = trainingJobs.values().stream()
				.filter(job -> job != null && job.twinId() != null && job.twinId().equals(twin.id()))
				.filter(job -> job.status() == TrainingJobStatus.DONE)
				.filter(job -> userId.equals(trainingJobOwnerById.get(job.jobId())))
				.sorted(trainingJobRecencyComparator())
				.toList();
		for (TrainingJob job : candidates) {
			TrainingTicketSnapshot snapshot = safeGetTrainingTicket(job.jobId());
			if (snapshot == null) continue;
			if (!isCompletedSnapshot(snapshot)) continue;
			if (resolveInferenceTarget(twin, snapshot.outcome(), subjectId).isEmpty()) continue;
			return Optional.of(snapshot);
		}
		return Optional.empty();
	}

	private TrainingTicketSnapshot safeGetTrainingTicket(String jobId) {
		if (jobId == null || jobId.isBlank()) return null;
		try {
			return getTrainingTicket(jobId);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Comparator<TrainingJob> trainingJobRecencyComparator() {
		return Comparator
				.comparing(TrainingJob::completedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TrainingJob::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.reversed();
	}

	private static boolean isCompletedSnapshot(TrainingTicketSnapshot snapshot) {
		if (snapshot == null || snapshot.status() == null) return false;
		return "completed".equalsIgnoreCase(snapshot.status().trim());
	}

	private InferenceEngine finalizeTraining(String userId, String twinId, TrainingTicketSnapshot snapshot) {
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine engine = twin.inferenceEngine();
		if (engine == null) return null;
		List<InferredVariableResult> inferred = buildInferredVariables(twin, engine, snapshot.outcome());
		Instant launchedAt = firstNonNull(snapshot.createdAt(), snapshot.startedAt(), engine.launchedAt());
		Instant finishedAt = firstNonNull(snapshot.finishedAt(), snapshot.updatedAt(), Instant.now());
		Double trainingDurationSeconds = resolveTrainingDurationSeconds(
				firstNonNull(snapshot.startedAt(), launchedAt),
				finishedAt,
				engine.trainingDurationSeconds()
		);

		InferenceEngine trained = new InferenceEngine(
				true,
				engine.algorithm() == null ? TrainingAlgorithm.KAN : engine.algorithm(),
				finishedAt,
				launchedAt,
				trainingDurationSeconds,
				positiveOrDefault(engine.epochs(), DEFAULT_EPOCHS),
				positiveOrDefault(engine.learningRate(), DEFAULT_LEARNING_RATE),
				nonNegativeOrDefault(engine.windowSize(), DEFAULT_WINDOW_SIZE),
				positiveOrDefault(engine.batchSize(), DEFAULT_BATCH_SIZE),
				inferred,
				engine.retrainingConfig()
		);

		DigitalTwin updatedTwin = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				trained,
				twin.datasets(),
				twin.ingestionToken()
		);
		appendInferencePredictionSample(updatedTwin, snapshot);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), updatedTwin);
		return UiCommandFixtures.copyInferenceEngine(trained);
	}

	private void appendInferencePredictionSample(DigitalTwin twin, TrainingTicketSnapshot snapshot) {
		appendInferencePredictionSampleForSubject(twin, snapshot, null);
	}

	private void appendInferencePredictionSampleForSubject(DigitalTwin twin, TrainingTicketSnapshot snapshot, String subjectId) {
		if (twin == null || snapshot == null) return;
		String ticketId = snapshot.ticketId();
		if (ticketId == null || ticketId.isBlank()) return;
		Optional<InferenceTarget> target = resolveInferenceTarget(twin, snapshot.outcome(), subjectId);
		if (target.isEmpty()) return;
		DigitalSubject subject = target.get().subject();
		Optional<Path> datasetPath = datasetStorage.resolveDatasetPath(twin.id(), subject.id(), subject.name());
		if (datasetPath.isEmpty()) return;
		Optional<LatestDatasetRow> latestDatasetRow = readLatestDatasetRow(datasetPath.get());
		if (latestDatasetRow.isEmpty()) return;
		LatestSensorValues latestSensorValues = readLatestSensorValues(twin, subject);
		Map<String, Double> variables = buildInferenceVariables(snapshot.outcome(), latestDatasetRow.get(), latestSensorValues);
		if (variables.isEmpty()) return;

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("training_ticket_id", ticketId.trim());
		request.put("output_scale", "raw");
		request.put("instances", List.of(Map.of("variables", variables)));
		TrainingInferenceResult inferenceResult = requestInferenceWithRetries(request);
		if (inferenceResult == null || inferenceResult.prediction() == null || !Double.isFinite(inferenceResult.prediction())) {
			return;
		}
		Instant inferredAt = inferenceResult.inferredAt() != null
				? inferenceResult.inferredAt()
				: latestSensorValues.instant() != null ? latestSensorValues.instant() : Instant.now();
		Variable inferredVariable = target.get().variable();
		datasetStorage.appendMetric(
				twin.id(),
				twin.version(),
				subject.id(),
				subject.name(),
				inferredVariable.id(),
				inferredVariable.name(),
				DatasetStorage.MetricKind.INFERRED,
				inferredAt,
				inferenceResult.prediction()
		);
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

	private Optional<InferenceTarget> resolveInferenceTarget(DigitalTwin twin, TrainingTicketOutcome outcome) {
		return resolveInferenceTarget(twin, outcome, null);
	}

	private Optional<InferenceTarget> resolveInferenceTarget(DigitalTwin twin, TrainingTicketOutcome outcome, String subjectIdFilter) {
		if (twin == null || outcome == null) return Optional.empty();
		String outputVariable = outcome.outputVariable();
		if (outputVariable == null || outputVariable.isBlank()) return Optional.empty();
		for (DigitalSubject subject : twin.subjects()) {
			if (subject == null || subject.variables() == null) continue;
			if (subjectIdFilter != null && !subjectIdFilter.isBlank() && !subjectIdFilter.equals(subject.id()))
				continue;
			for (Variable variable : subject.variables()) {
				if (variable == null || variable.variableType() != VariableType.INFERRED) continue;
				if (!matchesOutputVariable(variable, outputVariable)) continue;
				return Optional.of(new InferenceTarget(subject, variable));
			}
		}
		return Optional.empty();
	}

	private Map<String, Double> buildInferenceVariables(
			TrainingTicketOutcome outcome,
			LatestDatasetRow row,
			LatestSensorValues sensorValues
	) {
		if (outcome == null || row == null) return Map.of();
		List<String> inputVariables = outcome.inputVariables();
		if (inputVariables == null || inputVariables.isEmpty()) return Map.of();
		Map<String, String> rowValues = row.values();
		Instant referenceInstant = sensorValues == null ? null : sensorValues.instant();
		if (referenceInstant == null) referenceInstant = row.instant();
		if (referenceInstant == null) referenceInstant = Instant.now();
		ZonedDateTime timestamp = referenceInstant.atZone(ZoneOffset.UTC);
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

		Map<String, String> bestRow = null;
		Instant bestInstant = null;
		for (int i = 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) continue;
			List<String> cells = parseDelimitedLine(line, delimiter);
			Map<String, String> row = new LinkedHashMap<>();
			for (int col = 0; col < headers.size(); col++) {
				String header = headers.get(col);
				if (header == null || header.isBlank()) continue;
				String value = col < cells.size() ? cells.get(col) : "";
				row.put(header.trim(), value == null ? "" : value.trim());
			}
			Instant rowInstant = parseInstantLike(resolveColumnValue(row, "instant"));
			if (rowInstant != null) {
				if (bestInstant == null || rowInstant.isAfter(bestInstant)) {
					bestInstant = rowInstant;
					bestRow = row;
				}
				continue;
			}
			if (bestInstant == null) {
				bestRow = row;
			}
		}
		if (bestRow == null) return Optional.empty();
		return Optional.of(new LatestDatasetRow(bestInstant == null ? Instant.now() : bestInstant, Map.copyOf(bestRow)));
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

	private TrainingLaunchContext resolveLaunchContext(DigitalTwin twin) {
		List<SubjectDataset> uploadedDatasets = twin.datasets().stream()
				.filter(dataset -> dataset != null && dataset.subjectId() != null && !dataset.subjectId().isBlank())
				.filter(dataset -> dataset.uploadedRecords() != null && dataset.uploadedRecords() > 0)
				.toList();
		validate(!uploadedDatasets.isEmpty(), 422, "PRECONDITION_FAILED",
				"At least one subject must have an uploaded dataset before training");

		Map<String, DigitalSubject> subjectsById = new LinkedHashMap<>();
		for (DigitalSubject subject : twin.subjects()) {
			if (subject == null || subject.id() == null || subject.id().isBlank()) continue;
			subjectsById.put(subject.id(), subject);
		}

		boolean missingInferredVariable = false;
		boolean missingStoredFile = false;
		boolean missingSubjectResolution = false;
		for (SubjectDataset dataset : uploadedDatasets) {
			DigitalSubject subject = subjectsById.get(dataset.subjectId());
			if (subject == null) continue;
			Variable output = subject.variables().stream()
					.filter(variable -> variable != null && variable.variableType() == VariableType.INFERRED)
					.findFirst()
					.orElse(null);
			if (output == null) {
				missingInferredVariable = true;
				continue;
			}
			Optional<Path> datasetPath = datasetStorage.resolveDatasetPath(twin.id(), subject.id(), subject.name());
			if (datasetPath.isEmpty()) {
				missingStoredFile = true;
				continue;
			}
			TimeBucket timeBucket = subject.timeBucket();
			if (timeBucket == null) {
				missingSubjectResolution = true;
				continue;
			}
			String outputColumn = columnName(output);
			List<String> numericalInputs = new ArrayList<>();
			List<String> categoricalInputs = new ArrayList<>();
			for (Variable variable : subject.variables()) {
				if (variable == null || variable.variableType() != VariableType.SENSOR) continue;
				String candidateColumn = columnName(variable);
				if (candidateColumn.equalsIgnoreCase(outputColumn)) continue;
				if (variable.dataType() == VariableDataType.CATEGORICAL) {
					categoricalInputs.add(candidateColumn);
				} else {
					numericalInputs.add(candidateColumn);
				}
			}
			int resolvedTimeHorizon = positiveOrDefault(output.timeHorizon(), 1);
			int resolvedLookback = nonNegativeOrDefault(
					output.lookback(),
					nonNegativeOrDefault(twin.inferenceEngine() == null ? null : twin.inferenceEngine().windowSize(), DEFAULT_WINDOW_SIZE)
			);
			Path preparedDatasetPath = trainingDatasetPreparer.prepareSubjectTrainingDataset(
					twin.id(),
					twin.version(),
					subject,
					outputColumn,
					mergeInputColumns(numericalInputs, categoricalInputs),
					datasetPath.get(),
					timeBucket
			);
			return new TrainingLaunchContext(
					subject,
					outputColumn,
					preparedDatasetPath,
					numericalInputs,
					categoricalInputs,
					timeBucket,
					resolvedTimeHorizon,
					resolvedLookback
			);
		}
		if (missingStoredFile) {
			throw new UiCommandException(
					422,
					"PRECONDITION_FAILED",
					"Dataset file is missing on disk. Re-upload the dataset before training."
			);
		}
		if (missingSubjectResolution) {
			throw new UiCommandException(
					422,
					"PRECONDITION_FAILED",
					"Subject resolution is required to train inferred variables"
			);
		}
		if (missingInferredVariable) {
			throw new UiCommandException(
					422,
					"PRECONDITION_FAILED",
					"At least one inferred variable is required in a subject with uploaded dataset"
			);
		}
		throw new UiCommandException(422, "PRECONDITION_FAILED", "No valid dataset was found for training");
	}

	private Map<String, Object> buildTrainingRequest(String userId, DigitalTwin twin, TrainingLaunchContext launchContext) {
		InferenceEngine engine = twin.inferenceEngine();
		Map<String, Object> options = new LinkedHashMap<>();
		options.put("case_name", twin.name());
		options.put("timestamp_column", "instant");
		options.put("target_column", launchContext.outputVariable());
		options.put("time_bucket", launchContext.timeBucket().toWireValue());
		options.put("entity_key_columns", List.of());
		options.put("numerical_input_columns", launchContext.numericalInputColumns());
		options.put("categorical_input_columns", launchContext.categoricalInputColumns());
		options.put("numerical_scaler", "zscore");
		options.put("categorical_encoding", launchContext.categoricalInputColumns().isEmpty() ? "none" : "one_hot");

		Map<String, Object> dataSource = new LinkedHashMap<>();
		dataSource.put("kind", "tabular_timeseries");
		dataSource.put("path", launchContext.datasetPath().toAbsolutePath().normalize().toString());
		dataSource.put("options", options);

		Map<String, Object> architecture = new LinkedHashMap<>();
		architecture.put("family", toArchitectureFamily(engine.algorithm()));
		architecture.put("mode", "baseline");
		architecture.put("epochs", positiveOrDefault(engine.epochs(), DEFAULT_EPOCHS));
		architecture.put("batch_size", positiveOrDefault(engine.batchSize(), DEFAULT_BATCH_SIZE));
		architecture.put("learning_rate", positiveOrDefault(engine.learningRate(), DEFAULT_LEARNING_RATE));
		architecture.put("seed", 42);

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("job_name", "twin-" + twin.id() + "-v" + sanitizeVersion(twin.version()));
		request.put("created_by", userId);
		request.put("data_source", dataSource);
		request.put("target_variable", launchContext.outputVariable());
		request.put("lookback", launchContext.lookback());
		request.put("time_horizon", Map.of("value", launchContext.timeHorizon(), "unit", "steps"));
		request.put("split", Map.of("train_ratio", 0.64, "val_ratio", 0.16, "test_ratio", 0.20));
		request.put("architecture", architecture);
		return request;
	}

	private TrainingTicketAccepted createTrainingTicket(Map<String, Object> request) {
		try {
			return trainingClient.createTraining(request);
		} catch (TrainingApiException e) {
			throw mapTrainingApiError(e, "Unable to launch training");
		}
	}

	private TrainingTicketSnapshot getTrainingTicket(String jobId) {
		try {
			return trainingClient.getTraining(jobId);
		} catch (TrainingApiException e) {
			throw mapTrainingApiError(e, "Unable to fetch training status");
		}
	}

	private UiCommandException mapTrainingApiError(TrainingApiException error, String message) {
		String normalized = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
		if (normalized.contains("not configured")) {
			return new UiCommandException(503, "TRAINING_API_UNAVAILABLE", message + ": training API is not configured");
		}
		int upstreamStatus = error.statusCode();
		if (upstreamStatus == 404) {
			return new UiCommandException(404, "TRAINING_JOB_NOT_FOUND", "Training ticket does not exist in upstream API");
		}
		int status = upstreamStatus >= 400 && upstreamStatus < 500 ? 422 : 502;
		Map<String, Object> details = error.responseBody().isBlank()
				? Map.of("upstreamStatus", upstreamStatus)
				: Map.of(
				"upstreamStatus", upstreamStatus,
				"upstreamBody", truncate(error.responseBody(), MAX_UPSTREAM_BODY_LENGTH)
		);
		return new UiCommandException(status, "TRAINING_API_ERROR", message + ": " + error.getMessage(), details);
	}

	private TrainingJob requireTrainingJob(String userId, String twinId, String jobId) {
		TrainingJob current = trainingJobs.get(jobId);
		String ownerId = trainingJobOwnerById.get(jobId);
		if (current == null || !current.twinId().equals(twinId) || ownerId == null || !ownerId.equals(userId)) {
			throw new UiCommandException(404, "TRAINING_JOB_NOT_FOUND", "No training job found with id " + jobId);
		}
		return current;
	}

	private List<InferredVariableResult> buildInferredVariables(
			DigitalTwin twin,
			InferenceEngine currentEngine,
			TrainingTicketOutcome outcome
	) {
		if (outcome != null && outcome.outputVariable() != null && !outcome.outputVariable().isBlank()) {
			VariableDataType outputType = resolveOutcomeDataType(twin, outcome);
			boolean isCategorical = outputType == VariableDataType.CATEGORICAL;
			return List.of(new InferredVariableResult(
					outcome.outputVariable(),
					roundNullable(outcome.maeRaw(), 4),
					roundNullable(outcome.r2(), 4),
					outcome.testSamples(),
					roundNullable(outcome.testElapsedSeconds(), 3),
					outputType,
					isCategorical ? roundNullable(outcome.accuracy(), 4) : null,
					isCategorical ? roundNullable(outcome.macroF1(), 4) : null,
					violationPercentage(outcome.overallViolationRate()),
					constraintViolationPercentages(outcome.constraintViolationRates())
			));
		}
		if (currentEngine.inferredVariables() != null && !currentEngine.inferredVariables().isEmpty()) {
			return currentEngine.inferredVariables();
		}
		return twin.subjects().stream()
				.flatMap(subject -> subject.variables().stream())
				.filter(variable -> variable.variableType() == VariableType.INFERRED)
				.map(variable -> new InferredVariableResult(
						columnName(variable),
						null,
						null,
						null,
						null,
						variable.dataType(),
						null,
						null,
						null,
						Map.of()
				))
				.toList();
	}

	private TrainingJobStatus mapExternalStatus(String externalStatus, TrainingJob current) {
		if (externalStatus == null || externalStatus.isBlank()) {
			return current == null || current.status() == null ? TrainingJobStatus.QUEUED : current.status();
		}
		String normalized = externalStatus.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "queued" -> current == null ? TrainingJobStatus.QUEUED : TrainingJobStatus.PREPARING;
			case "running" -> current != null && current.status() == TrainingJobStatus.PREPARING
					? TrainingJobStatus.TRAINING
					: current != null && current.status() == TrainingJobStatus.QUEUED
					? TrainingJobStatus.PREPARING
					: TrainingJobStatus.TRAINING;
			case "completed" -> TrainingJobStatus.DONE;
			case "failed" -> TrainingJobStatus.FAILED;
			default -> current == null || current.status() == null ? TrainingJobStatus.QUEUED : current.status();
		};
	}

	private Integer resolveProgress(TrainingJobStatus status, Integer currentProgress, TrainingTicketSnapshot snapshot) {
		int current = currentProgress == null ? 0 : currentProgress;
		Integer epochsProgress = progressFromEpochs(snapshot);
		return switch (status) {
			case DONE -> 100;
			case FAILED -> epochsProgress != null ? clamp(epochsProgress, 0, 100) : (current > 0 ? current : 100);
			case QUEUED -> Math.max(current, 5);
			case PREPARING -> clamp(Math.max(current + 8, 15), 10, 35);
			case TRAINING -> epochsProgress == null
					? clamp(Math.max(current + 12, 40), 35, 92)
					: clamp(Math.max(epochsProgress, current), 0, 99);
			case EVALUATING -> epochsProgress == null
					? clamp(Math.max(current + 6, 93), 90, 98)
					: clamp(Math.max(epochsProgress, 95), 90, 99);
		};
	}

	private String phaseForSnapshot(TrainingJobStatus status, TrainingTicketSnapshot snapshot) {
		if (status == TrainingJobStatus.FAILED) return "Training failed";
		Optional<String> epochLabel = epochLabel(snapshot);
		if (status == TrainingJobStatus.TRAINING && epochLabel.isPresent()) {
			return "Training epoch " + epochLabel.get() + "…";
		}
		if (status == TrainingJobStatus.EVALUATING && epochLabel.isPresent()) {
			return "Evaluating model… (" + epochLabel.get() + ")";
		}
		return phaseForStatus(status);
	}

	private Integer progressFromEpochs(TrainingTicketSnapshot snapshot) {
		if (snapshot == null) return null;
		if (snapshot.progressPercent() != null && Double.isFinite(snapshot.progressPercent())) {
			return (int) Math.round(snapshot.progressPercent());
		}
		Integer completed = snapshot.epochsCompleted();
		Integer total = snapshot.epochsTotal();
		if (completed == null || total == null || total <= 0) return null;
		return (int) Math.round((completed * 100.0) / total);
	}

	private Optional<String> epochLabel(TrainingTicketSnapshot snapshot) {
		if (snapshot == null) return Optional.empty();
		Integer completed = snapshot.epochsCompleted();
		Integer total = snapshot.epochsTotal();
		if (completed == null || total == null || total <= 0) return Optional.empty();
		return Optional.of(clamp(completed, 0, total) + "/" + total);
	}

	private String phaseForStatus(TrainingJobStatus status) {
		return switch (status) {
			case QUEUED -> "Queued";
			case PREPARING -> "Preparing dataset…";
			case TRAINING -> "Training in progress…";
			case EVALUATING -> "Evaluating model…";
			case DONE -> "Training complete";
			case FAILED -> "Training failed";
		};
	}

	private Instant selectStartedAt(TrainingJob current, TrainingTicketSnapshot snapshot, TrainingJobStatus nextStatus) {
		if (snapshot.startedAt() != null) return snapshot.startedAt();
		if (current.startedAt() != null) return current.startedAt();
		if (nextStatus == TrainingJobStatus.TRAINING
				|| nextStatus == TrainingJobStatus.EVALUATING
				|| nextStatus == TrainingJobStatus.DONE
				|| nextStatus == TrainingJobStatus.FAILED) {
			return Instant.now();
		}
		return null;
	}

	private Instant selectCompletedAt(TrainingJob current, TrainingTicketSnapshot snapshot) {
		if (snapshot.finishedAt() != null) return snapshot.finishedAt();
		if (current.completedAt() != null) return current.completedAt();
		return Instant.now();
	}

	private static boolean isTerminal(TrainingJobStatus status) {
		return status == TrainingJobStatus.DONE || status == TrainingJobStatus.FAILED;
	}

	private static String normalizeTicketId(String rawTicketId) {
		if (rawTicketId == null || rawTicketId.isBlank()) {
			throw new UiCommandException(502, "TRAINING_API_ERROR", "Training API did not return a ticket id");
		}
		return rawTicketId.trim();
	}

	private static String toArchitectureFamily(TrainingAlgorithm algorithm) {
		if (algorithm == null) return "kan";
		return switch (algorithm) {
			case KAN -> "kan";
			case TABNET -> "tabnet";
		};
	}

	private static String sanitizeVersion(String version) {
		if (version == null || version.isBlank()) return "0_0_0";
		return version.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private static List<String> mergeInputColumns(List<String> numerical, List<String> categorical) {
		LinkedHashSet<String> columns = new LinkedHashSet<>();
		if (numerical != null) columns.addAll(numerical);
		if (categorical != null) columns.addAll(categorical);
		return List.copyOf(columns);
	}

	private static Double resolveTrainingDurationSeconds(Instant startedAt, Instant finishedAt, Double fallback) {
		if (startedAt == null || finishedAt == null) return fallback;
		if (finishedAt.isBefore(startedAt)) return fallback;
		double seconds = Duration.between(startedAt, finishedAt).toMillis() / 1000.0;
		return round(seconds, 3);
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		if (values == null) return null;
		for (T value : values) {
			if (value != null) return value;
		}
		return null;
	}

	private static String columnName(Variable variable) {
		if (variable == null) return "variable";
		if (variable.name() != null && !variable.name().isBlank()) return variable.name().trim();
		if (variable.id() != null && !variable.id().isBlank()) return variable.id().trim();
		return "variable";
	}

	private static Double violationPercentage(Double value) {
		if (value == null || !Double.isFinite(value)) return null;
		double percentage = value <= 1.0 ? value * 100.0 : value;
		return round(Math.max(percentage, 0), 2);
	}

	private static VariableDataType resolveOutcomeDataType(DigitalTwin twin, TrainingTicketOutcome outcome) {
		if (outcome == null) return VariableDataType.NUMERIC;
		String output = outcome.outputVariable();
		if (output != null && !output.isBlank()) {
			Optional<VariableDataType> configuredType = twin.subjects().stream()
					.filter(subject -> subject != null && subject.variables() != null)
					.flatMap(subject -> subject.variables().stream())
					.filter(variable -> variable != null && matchesOutputVariable(variable, output))
					.map(Variable::dataType)
					.filter(Objects::nonNull)
					.findFirst();
			if (configuredType.isPresent()) {
				return configuredType.get();
			}
		}
		if (outcome.accuracy() != null || outcome.macroF1() != null) {
			return VariableDataType.CATEGORICAL;
		}
		return VariableDataType.NUMERIC;
	}

	private static boolean matchesOutputVariable(Variable variable, String output) {
		String normalizedOutput = output.trim();
		if (normalizedOutput.isBlank()) return false;
		if (variable.name() != null && variable.name().trim().equalsIgnoreCase(normalizedOutput)) return true;
		return variable.id() != null && variable.id().trim().equalsIgnoreCase(normalizedOutput);
	}

	private static Double roundNullable(Double value, int decimals) {
		if (value == null || !Double.isFinite(value)) return null;
		return round(value, decimals);
	}

	private static Map<String, Double> constraintViolationPercentages(Map<String, Double> rawRates) {
		if (rawRates == null || rawRates.isEmpty()) return Map.of();
		Map<String, Double> percentages = new LinkedHashMap<>();
		rawRates.forEach((constraint, rawValue) -> {
			if (constraint == null || constraint.isBlank()) return;
			Double percentage = violationPercentage(rawValue);
			if (percentage == null) return;
			percentages.put(constraint, percentage);
		});
		return percentages;
	}

	private static int positiveOrDefault(Integer value, int fallback) {
		if (value == null || value <= 0) return fallback;
		return value;
	}

	private static double positiveOrDefault(Double value, double fallback) {
		if (value == null || !Double.isFinite(value) || value <= 0.0) return fallback;
		return value;
	}

	private static int nonNegativeOrDefault(Integer value, int fallback) {
		if (value == null || value < 0) return fallback;
		return value;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Double round(double value, int decimals) {
		double factor = Math.pow(10, Math.max(0, decimals));
		return Math.round(value * factor) / factor;
	}

	private static Optional<String> nonBlank(String value) {
		if (value == null || value.isBlank()) return Optional.empty();
		return Optional.of(value.trim());
	}

	private static String truncate(String value, int limit) {
		if (value == null) return "";
		if (value.length() <= limit) return value;
		return value.substring(0, Math.max(0, limit)) + "…";
	}

	private DigitalTwin requireTwin(String userId, String twinId) {
		DigitalTwin twin = twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).get(twinId);
		if (twin == null) throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
		return twin;
	}

	private static void validate(boolean condition, int status, String code, String message) {
		if (!condition) throw new UiCommandException(status, code, message);
	}

	private record TrainingLaunchContext(
			DigitalSubject subject,
			String outputVariable,
			Path datasetPath,
			List<String> numericalInputColumns,
			List<String> categoricalInputColumns,
			TimeBucket timeBucket,
			Integer timeHorizon,
			Integer lookback
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
