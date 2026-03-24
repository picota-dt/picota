package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.training.*;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.persistence.DatasetStorage;

import java.nio.file.Path;
import java.time.Instant;
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
				twin.datasets()
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
				twin.datasets()
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

	private InferenceEngine finalizeTraining(String userId, String twinId, TrainingTicketSnapshot snapshot) {
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine engine = twin.inferenceEngine();
		if (engine == null) return null;
		List<InferredVariableResult> inferred = buildInferredVariables(twin, engine, snapshot.outcome());

		InferenceEngine trained = new InferenceEngine(
				true,
				engine.algorithm() == null ? TrainingAlgorithm.KAN : engine.algorithm(),
				snapshot.finishedAt() == null
						? snapshot.updatedAt() == null ? Instant.now() : snapshot.updatedAt()
						: snapshot.finishedAt(),
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
				twin.datasets()
		);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), updatedTwin);
		return UiCommandFixtures.copyInferenceEngine(trained);
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
				if (variable == null) continue;
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
			return new TrainingLaunchContext(
					subject,
					outputColumn,
					datasetPath.get(),
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
		if (outcome.accuracy() != null || outcome.macroF1() != null) {
			return VariableDataType.CATEGORICAL;
		}
		String output = outcome.outputVariable();
		if (output == null || output.isBlank()) return VariableDataType.NUMERIC;
		return twin.subjects().stream()
				.filter(subject -> subject != null && subject.variables() != null)
				.flatMap(subject -> subject.variables().stream())
				.filter(variable -> variable != null && matchesOutputVariable(variable, output))
				.map(Variable::dataType)
				.filter(type -> type != null)
				.findFirst()
				.orElse(VariableDataType.NUMERIC);
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
}
