package io.picota.backend.control.commands.real.state;

import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.training.*;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.persistence.DatasetStorage;

import java.nio.file.Path;
import java.time.Duration;
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
	private final ConcurrentMap<String, TrainingBatchState> trainingBatchByJobId = new ConcurrentHashMap<>();
	private final DatasetStorage datasetStorage;
	private final ExternalTrainingClient trainingClient;
	private final TrainingDatasetPreparer trainingDatasetPreparer;
	private final TrainingJobLifecycle jobLifecycle;
	private final TrainingInferenceWriter inferenceWriter;
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
		this.jobLifecycle = new TrainingJobLifecycle();
		this.inferenceWriter = new TrainingInferenceWriter(this.datasetStorage, this.trainingClient);
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
		List<TrainingLaunchContext> launchContexts = resolveLaunchContexts(twin);
		validate(!launchContexts.isEmpty(), 422, "PRECONDITION_FAILED", "No valid inference models were found to train");

		boolean hasRunning = trainingJobs.values().stream()
				.anyMatch(job -> job.twinId().equals(twinId) &&
						(job.status() == TrainingJobStatus.QUEUED
								|| job.status() == TrainingJobStatus.PREPARING
								|| job.status() == TrainingJobStatus.TRAINING
								|| job.status() == TrainingJobStatus.EVALUATING));
		if (hasRunning) {
			throw new UiCommandException(409, "TRAINING_ALREADY_RUNNING", "A training job is already in progress for this twin");
		}

		List<ChildTrainingTicket> childTickets = new ArrayList<>();
		List<TrainingJobStatus> childStatuses = new ArrayList<>();
		Instant createdAt = null;
		for (TrainingLaunchContext launchContext : launchContexts) {
			Map<String, Object> request = buildTrainingRequest(userId, twin, launchContext);
			TrainingTicketAccepted accepted = createTrainingTicket(request);
			String ticketId = normalizeTicketId(accepted.ticketId());
			Instant ticketCreatedAt = accepted.createdAt() == null ? Instant.now() : accepted.createdAt();
			createdAt = createdAt == null || ticketCreatedAt.isBefore(createdAt) ? ticketCreatedAt : createdAt;
			childTickets.add(new ChildTrainingTicket(ticketId, launchContext, ticketCreatedAt));
			TrainingJobStatus childStatus = jobLifecycle.mapExternalStatus(accepted.status(), null);
			childStatuses.add(childStatus);
			inferenceWriter.rememberTemporalContext(
					ticketId,
					launchContext.subject().id(),
					launchContext.outputVariable(),
					launchContext.timeBucket(),
					launchContext.predictionTimeHorizon(),
					launchContext.requestTimeHorizon()
			);
		}
		String jobId = resolveRootJobId(childTickets);
		trainingBatchByJobId.put(jobId, new TrainingBatchState(childTickets));
		TrainingJobStatus initialStatus = aggregateStatus(childStatuses, false);
		int initialProgress = aggregateProgress(childStatuses, List.<ChildTicketPollResult>of(), null);
		TrainingJob job = new TrainingJob(
				jobId,
				twinId,
				initialStatus,
				initialProgress,
				phaseForAggregateStatus(initialStatus, childStatuses.size(), 0),
				createdAt == null ? Instant.now() : createdAt,
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
		if (current.status() != null && jobLifecycle.isTerminal(current.status())) {
			return current;
		}
		TrainingBatchState batchState = trainingBatchByJobId.get(jobId);
		if (batchState == null || batchState.tickets().isEmpty()) {
			return pollSingleTicketJob(userId, twinId, current, jobId);
		}

		List<ChildTicketPollResult> childResults = pollChildTickets(batchState, current);
		List<TrainingJobStatus> childStatuses = childResults.stream().map(ChildTicketPollResult::status).toList();
		long completedChildren = childStatuses.stream().filter(status -> status == TrainingJobStatus.DONE).count();
		TrainingJobStatus nextStatus = aggregateStatus(childStatuses, childResults.stream().anyMatch(result -> result.snapshot() != null && result.snapshot().outcome() != null));
		Integer nextProgress = aggregateProgress(childStatuses, childResults, current.progress());
		Instant startedAt = resolveAggregateStartedAt(current, childResults, nextStatus);
		Instant completedAt = jobLifecycle.isTerminal(nextStatus) ? resolveAggregateCompletedAt(current, childResults) : null;
		String errorMessage = nextStatus == TrainingJobStatus.FAILED
				? aggregateErrorMessage(childResults)
				: null;

		InferenceEngine result = current.result();
		if (nextStatus == TrainingJobStatus.DONE) {
			List<TrainingCompletedSnapshot> completedSnapshots = childResults.stream()
					.filter(resultItem -> isCompletedSnapshot(resultItem.snapshot()))
					.map(resultItem -> new TrainingCompletedSnapshot(resultItem.snapshot(), resultItem.ticket().launchContext()))
					.toList();
			result = finalizeTraining(userId, twinId, completedSnapshots);
		}

		TrainingJob updated = new TrainingJob(
				current.jobId(),
				current.twinId(),
				nextStatus,
				nextProgress,
				phaseForAggregateStatus(nextStatus, childStatuses.size(), (int) completedChildren),
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
			inferenceWriter.appendInferencePredictionSampleForSubject(twin, snapshot.get(), subjectId);
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
			List<TrainingTicketSnapshot> snapshots = resolveSnapshotsForJob(job.jobId());
			for (TrainingTicketSnapshot snapshot : snapshots) {
				if (!isCompletedSnapshot(snapshot)) continue;
				if (!inferenceWriter.hasInferenceTarget(twin, snapshot.outcome(), subjectId)) continue;
				return Optional.of(snapshot);
			}
		}
		return Optional.empty();
	}

	private List<TrainingTicketSnapshot> resolveSnapshotsForJob(String jobId) {
		if (jobId == null || jobId.isBlank()) return List.of();
		TrainingBatchState batchState = trainingBatchByJobId.get(jobId);
		if (batchState == null || batchState.tickets().isEmpty()) {
			TrainingTicketSnapshot single = safeGetTrainingTicket(jobId);
			if (single == null) return List.of();
			return List.of(single);
		}
		List<TrainingTicketSnapshot> snapshots = new ArrayList<>();
		for (ChildTrainingTicket ticket : batchState.tickets()) {
			TrainingTicketSnapshot snapshot = safeGetTrainingTicket(ticket.ticketId());
			if (snapshot != null) snapshots.add(snapshot);
		}
		snapshots.sort(Comparator
				.comparing(TrainingTicketSnapshot::finishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(TrainingTicketSnapshot::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.reversed());
		return List.copyOf(snapshots);
	}

	private TrainingTicketSnapshot safeGetTrainingTicket(String ticketId) {
		if (ticketId == null || ticketId.isBlank()) return null;
		try {
			return getTrainingTicket(ticketId);
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

	private InferenceEngine finalizeTraining(String userId, String twinId, List<TrainingCompletedSnapshot> completedSnapshots) {
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine engine = twin.inferenceEngine();
		if (engine == null) return null;
		List<TrainingCompletedSnapshot> safeSnapshots = completedSnapshots == null ? List.of() : completedSnapshots.stream()
																								 .filter(snapshot -> snapshot != null && snapshot.snapshot() != null)
																								 .toList();
		List<InferredVariableResult> inferred = buildInferredVariables(twin, engine, safeSnapshots);
		Instant launchedAt = firstNonNull(earliestCreatedOrStartedAt(safeSnapshots), engine.launchedAt(), Instant.now());
		Instant finishedAt = firstNonNull(latestFinishedOrUpdatedAt(safeSnapshots), Instant.now());
		Double trainingDurationSeconds = resolveTrainingDurationSeconds(
				firstNonNull(earliestStartedAt(safeSnapshots), launchedAt),
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
		for (TrainingCompletedSnapshot snapshot : safeSnapshots) {
			inferenceWriter.appendInferencePredictionSample(updatedTwin, snapshot.snapshot());
		}
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), updatedTwin);
		return UiCommandFixtures.copyInferenceEngine(trained);
	}


	private List<TrainingLaunchContext> resolveLaunchContexts(DigitalTwin twin) {
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
		List<TrainingLaunchContext> launchContexts = new ArrayList<>();
		for (SubjectDataset dataset : uploadedDatasets) {
			DigitalSubject subject = subjectsById.get(dataset.subjectId());
			if (subject == null) continue;
			List<Variable> inferredOutputs = subject.variables().stream()
					.filter(variable -> variable != null && variable.variableType() == VariableType.INFERRED)
					.toList();
			if (inferredOutputs.isEmpty()) {
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
			List<Variable> sensors = subject.variables().stream()
					.filter(variable -> variable != null && variable.variableType() == VariableType.SENSOR)
					.toList();
			for (Variable output : inferredOutputs) {
				String outputColumn = columnName(output);
				List<String> numericalInputs = new ArrayList<>();
				List<String> categoricalInputs = new ArrayList<>();
				for (Variable sensor : sensors) {
					String candidateColumn = columnName(sensor);
					if (candidateColumn.equalsIgnoreCase(outputColumn)) continue;
					if (sensor.dataType() == VariableDataType.CATEGORICAL) {
						categoricalInputs.add(candidateColumn);
					} else {
						numericalInputs.add(candidateColumn);
					}
				}
				int resolvedTimeHorizon = positiveOrDefault(output.timeHorizon(), 1);
				Integer predictionTimeHorizon = output.timeHorizon();
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
				launchContexts.add(new TrainingLaunchContext(
						subject,
						outputColumn,
						preparedDatasetPath,
						numericalInputs,
						categoricalInputs,
						timeBucket,
						resolvedTimeHorizon,
						predictionTimeHorizon,
						resolvedLookback
				));
			}
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
		if (launchContexts.isEmpty()) {
			throw new UiCommandException(422, "PRECONDITION_FAILED", "No valid dataset was found for training");
		}
		return List.copyOf(launchContexts);
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
		request.put("job_name", trainingJobName(twin, launchContext));
		request.put("created_by", userId);
		request.put("data_source", dataSource);
		request.put("target_variable", launchContext.outputVariable());
		request.put("lookback", launchContext.lookback());
		request.put("time_horizon", Map.of("value", launchContext.requestTimeHorizon(), "unit", "steps"));
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

	private TrainingJob pollSingleTicketJob(String userId, String twinId, TrainingJob current, String ticketId) {
		TrainingTicketSnapshot snapshot = getTrainingTicket(ticketId);
		TrainingJobStatus nextStatus = jobLifecycle.mapExternalStatus(snapshot.status(), current);
		if (nextStatus == TrainingJobStatus.TRAINING && snapshot.outcome() != null) {
			nextStatus = TrainingJobStatus.EVALUATING;
		}
		Integer nextProgress = jobLifecycle.resolveProgress(nextStatus, current.progress(), snapshot);
		Instant startedAt = jobLifecycle.selectStartedAt(current, snapshot, nextStatus);
		Instant completedAt = jobLifecycle.isTerminal(nextStatus) ? jobLifecycle.selectCompletedAt(current, snapshot) : null;
		String errorMessage = nextStatus == TrainingJobStatus.FAILED
				? nonBlank(snapshot.errorMessage()).orElse("Training failed")
				: null;

		InferenceEngine result = current.result();
		if (nextStatus == TrainingJobStatus.DONE) {
			result = finalizeTraining(userId, twinId, List.of(new TrainingCompletedSnapshot(snapshot, null)));
		}

		TrainingJob updated = new TrainingJob(
				current.jobId(),
				current.twinId(),
				nextStatus,
				nextProgress,
				jobLifecycle.phaseForSnapshot(nextStatus, snapshot),
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

	private String resolveRootJobId(List<ChildTrainingTicket> childTickets) {
		if (childTickets == null || childTickets.isEmpty()) {
			throw new UiCommandException(502, "TRAINING_API_ERROR", "Training API did not return any training tickets");
		}
		return childTickets.get(0).ticketId();
	}

	private List<ChildTicketPollResult> pollChildTickets(TrainingBatchState batchState, TrainingJob current) {
		List<ChildTicketPollResult> results = new ArrayList<>();
		for (ChildTrainingTicket ticket : batchState.tickets()) {
			TrainingTicketSnapshot snapshot = getTrainingTicket(ticket.ticketId());
			TrainingJobStatus status = jobLifecycle.mapExternalStatus(snapshot.status(), current);
			if (status == TrainingJobStatus.TRAINING && snapshot.outcome() != null) {
				status = TrainingJobStatus.EVALUATING;
			}
			Integer progress = jobLifecycle.resolveProgress(status, null, snapshot);
			results.add(new ChildTicketPollResult(ticket, snapshot, status, progress));
		}
		return List.copyOf(results);
	}

	private static TrainingJobStatus aggregateStatus(List<TrainingJobStatus> childStatuses, boolean anyOutcomeAvailable) {
		if (childStatuses == null || childStatuses.isEmpty()) return TrainingJobStatus.QUEUED;
		if (childStatuses.stream().anyMatch(status -> status == TrainingJobStatus.FAILED))
			return TrainingJobStatus.FAILED;
		boolean allDone = childStatuses.stream().allMatch(status -> status == TrainingJobStatus.DONE);
		if (allDone) return TrainingJobStatus.DONE;
		if (childStatuses.stream().anyMatch(status -> status == TrainingJobStatus.EVALUATING))
			return TrainingJobStatus.EVALUATING;
		if (childStatuses.stream().anyMatch(status -> status == TrainingJobStatus.TRAINING)) {
			return anyOutcomeAvailable ? TrainingJobStatus.EVALUATING : TrainingJobStatus.TRAINING;
		}
		if (childStatuses.stream().anyMatch(status -> status == TrainingJobStatus.PREPARING))
			return TrainingJobStatus.PREPARING;
		return TrainingJobStatus.QUEUED;
	}

	private int aggregateProgress(
			List<TrainingJobStatus> childStatuses,
			List<ChildTicketPollResult> childResults,
			Integer currentProgress
	) {
		int current = currentProgress == null ? 0 : currentProgress;
		if (childStatuses == null || childStatuses.isEmpty()) {
			return clamp(Math.max(current, 0), 0, 100);
		}
		TrainingJobStatus aggregateStatus = aggregateStatus(childStatuses, childResults != null && childResults.stream().anyMatch(result -> result.snapshot() != null && result.snapshot().outcome() != null));
		if (aggregateStatus == TrainingJobStatus.DONE) return 100;

		double average;
		if (childResults == null || childResults.isEmpty()) {
			average = childStatuses.stream()
					.mapToInt(status -> jobLifecycle.resolveProgress(status, null, null))
					.average()
					.orElse(0.0);
		} else {
			average = childResults.stream()
					.map(ChildTicketPollResult::progress)
					.filter(Objects::nonNull)
					.mapToInt(Integer::intValue)
					.average()
					.orElse(0.0);
		}
		int rounded = (int) Math.round(average);
		if (aggregateStatus == TrainingJobStatus.FAILED) {
			return clamp(Math.max(current, rounded), 0, 100);
		}
		return clamp(Math.max(current, rounded), 0, 99);
	}

	private static Instant resolveAggregateStartedAt(
			TrainingJob current,
			List<ChildTicketPollResult> childResults,
			TrainingJobStatus nextStatus
	) {
		Instant earliest = null;
		if (childResults != null) {
			for (ChildTicketPollResult result : childResults) {
				TrainingTicketSnapshot snapshot = result.snapshot();
				if (snapshot == null || snapshot.startedAt() == null) continue;
				if (earliest == null || snapshot.startedAt().isBefore(earliest)) {
					earliest = snapshot.startedAt();
				}
			}
		}
		if (earliest != null) return earliest;
		if (current != null && current.startedAt() != null) return current.startedAt();
		if (nextStatus == TrainingJobStatus.TRAINING
				|| nextStatus == TrainingJobStatus.EVALUATING
				|| nextStatus == TrainingJobStatus.DONE
				|| nextStatus == TrainingJobStatus.FAILED) {
			return Instant.now();
		}
		return null;
	}

	private static Instant resolveAggregateCompletedAt(TrainingJob current, List<ChildTicketPollResult> childResults) {
		Instant latest = null;
		if (childResults != null) {
			for (ChildTicketPollResult result : childResults) {
				TrainingTicketSnapshot snapshot = result.snapshot();
				if (snapshot == null) continue;
				Instant candidate = firstNonNull(snapshot.finishedAt(), snapshot.updatedAt());
				if (candidate == null) continue;
				if (latest == null || candidate.isAfter(latest)) {
					latest = candidate;
				}
			}
		}
		if (latest != null) return latest;
		if (current != null && current.completedAt() != null) return current.completedAt();
		return Instant.now();
	}

	private static String aggregateErrorMessage(List<ChildTicketPollResult> childResults) {
		if (childResults == null || childResults.isEmpty()) return "Training failed";
		for (ChildTicketPollResult result : childResults) {
			if (result.status() != TrainingJobStatus.FAILED) continue;
			String message = result.snapshot() == null ? null : result.snapshot().errorMessage();
			if (message == null || message.isBlank()) continue;
			return "Ticket " + result.ticket().ticketId() + " failed: " + message.trim();
		}
		return "Training failed for one or more inference models";
	}

	private String phaseForAggregateStatus(TrainingJobStatus status, int totalTickets, int completedTickets) {
		int total = Math.max(1, totalTickets);
		int completed = Math.max(0, Math.min(completedTickets, total));
		if (total == 1) return jobLifecycle.phaseForStatus(status);
		return switch (status) {
			case QUEUED -> "Queued (" + total + " models)";
			case PREPARING -> "Preparing " + total + " models...";
			case TRAINING -> "Training models (" + completed + "/" + total + ")...";
			case EVALUATING -> "Evaluating models (" + completed + "/" + total + ")...";
			case DONE -> "Training complete";
			case FAILED -> "Training failed";
		};
	}

	private static Instant earliestCreatedOrStartedAt(List<TrainingCompletedSnapshot> snapshots) {
		if (snapshots == null || snapshots.isEmpty()) return null;
		Instant earliest = null;
		for (TrainingCompletedSnapshot snapshot : snapshots) {
			if (snapshot == null || snapshot.snapshot() == null) continue;
			Instant candidate = firstNonNull(snapshot.snapshot().createdAt(), snapshot.snapshot().startedAt());
			if (candidate == null) continue;
			if (earliest == null || candidate.isBefore(earliest)) {
				earliest = candidate;
			}
		}
		return earliest;
	}

	private static Instant earliestStartedAt(List<TrainingCompletedSnapshot> snapshots) {
		if (snapshots == null || snapshots.isEmpty()) return null;
		Instant earliest = null;
		for (TrainingCompletedSnapshot snapshot : snapshots) {
			if (snapshot == null || snapshot.snapshot() == null || snapshot.snapshot().startedAt() == null) continue;
			Instant candidate = snapshot.snapshot().startedAt();
			if (earliest == null || candidate.isBefore(earliest)) {
				earliest = candidate;
			}
		}
		return earliest;
	}

	private static Instant latestFinishedOrUpdatedAt(List<TrainingCompletedSnapshot> snapshots) {
		if (snapshots == null || snapshots.isEmpty()) return null;
		Instant latest = null;
		for (TrainingCompletedSnapshot snapshot : snapshots) {
			if (snapshot == null || snapshot.snapshot() == null) continue;
			Instant candidate = firstNonNull(snapshot.snapshot().finishedAt(), snapshot.snapshot().updatedAt());
			if (candidate == null) continue;
			if (latest == null || candidate.isAfter(latest)) {
				latest = candidate;
			}
		}
		return latest;
	}

	private static String inferredVariableKey(Variable variable) {
		if (variable == null) return "inferred";
		String byId = normalizeKey(trimToNull(variable.id()));
		if (byId != null) return byId;
		return inferredOutcomeKey(columnName(variable), variable.timeHorizon());
	}

	private static String inferredOutcomeKey(String outputVariable, Integer predictionHorizon) {
		String normalizedOutput = normalizeKey(trimToNull(outputVariable));
		if (normalizedOutput == null) normalizedOutput = "inferred";
		if (predictionHorizon != null && predictionHorizon > 0) {
			return normalizedOutput + "__t_plus_" + predictionHorizon;
		}
		return normalizedOutput + "__inferred";
	}

	private static String normalizeKey(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		if (trimmed.isEmpty()) return null;
		return trimmed.toLowerCase(Locale.ROOT);
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private Variable resolveMatchedInferenceVariable(DigitalTwin twin, TrainingCompletedSnapshot completed) {
		if (twin == null || completed == null || completed.snapshot() == null || completed.snapshot().outcome() == null)
			return null;
		TrainingTicketOutcome outcome = completed.snapshot().outcome();
		String outputVariable = trimToNull(outcome.outputVariable());
		if (outputVariable == null) return null;
		TrainingLaunchContext context = completed.launchContext();
		List<VariableCandidate> candidates = new ArrayList<>();
		for (DigitalSubject subject : twin.subjects()) {
			if (subject == null || subject.variables() == null) continue;
			if (context != null && context.subject() != null && context.subject().id() != null
					&& subject.id() != null && !context.subject().id().equalsIgnoreCase(subject.id())) {
				continue;
			}
			for (Variable variable : subject.variables()) {
				if (variable == null || variable.variableType() != VariableType.INFERRED) continue;
				if (!matchesOutputVariable(variable, outputVariable)) continue;
				candidates.add(new VariableCandidate(subject, variable));
			}
		}
		if (candidates.isEmpty()) return null;
		if (context == null || context.predictionTimeHorizon() == null) {
			return candidates.get(0).variable();
		}
		for (VariableCandidate candidate : candidates) {
			if (Objects.equals(candidate.variable().timeHorizon(), context.predictionTimeHorizon())) {
				return candidate.variable();
			}
		}
		return candidates.get(0).variable();
	}

	private String trainingJobName(DigitalTwin twin, TrainingLaunchContext launchContext) {
		String base = "twin-" + sanitizeToken(twin == null ? null : twin.id()) + "-v" + sanitizeVersion(twin == null ? null : twin.version());
		if (launchContext == null) return base;
		String subjectToken = sanitizeToken(launchContext.subject() == null ? null : launchContext.subject().id());
		String outputToken = sanitizeToken(launchContext.outputVariable());
		Integer horizon = firstNonNull(launchContext.predictionTimeHorizon(), launchContext.requestTimeHorizon(), 1);
		return base + "-" + subjectToken + "-" + outputToken + "-h" + Math.max(1, horizon);
	}

	private static String sanitizeToken(String value) {
		String normalized = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
		return normalized.isBlank() ? "item" : normalized;
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
			List<TrainingCompletedSnapshot> completedSnapshots
	) {
		Map<String, Variable> inferredVariablesByKey = new LinkedHashMap<>();
		for (DigitalSubject subject : twin.subjects()) {
			if (subject == null || subject.variables() == null) continue;
			for (Variable variable : subject.variables()) {
				if (variable == null || variable.variableType() != VariableType.INFERRED) continue;
				inferredVariablesByKey.put(inferredVariableKey(variable), variable);
			}
		}

		Map<String, InferredVariableResult> resultsByKey = new LinkedHashMap<>();
		inferredVariablesByKey.forEach((key, variable) -> resultsByKey.put(
				key,
				new InferredVariableResult(
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
				)
		));

		List<TrainingCompletedSnapshot> safeSnapshots = completedSnapshots == null ? List.of() : completedSnapshots.stream()
																								 .filter(item -> item != null && item.snapshot() != null && item.snapshot().outcome() != null)
																								 .toList();
		for (TrainingCompletedSnapshot completed : safeSnapshots) {
			TrainingTicketOutcome outcome = completed.snapshot().outcome();
			Variable matched = resolveMatchedInferenceVariable(twin, completed);
			VariableDataType outputType = matched == null ? resolveOutcomeDataType(twin, outcome) : matched.dataType();
			boolean isCategorical = outputType == VariableDataType.CATEGORICAL;
			String key = matched == null
					? inferredOutcomeKey(outcome.outputVariable(), completed.launchContext() == null ? null : completed.launchContext().predictionTimeHorizon())
					: inferredVariableKey(matched);
			String displayName = matched == null ? outcome.outputVariable() : columnName(matched);
			resultsByKey.put(key, new InferredVariableResult(
					displayName,
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

		if (!resultsByKey.isEmpty()) {
			return List.copyOf(resultsByKey.values());
		}
		if (currentEngine != null && currentEngine.inferredVariables() != null && !currentEngine.inferredVariables().isEmpty()) {
			return currentEngine.inferredVariables();
		}
		return List.of();
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
			Integer requestTimeHorizon,
			Integer predictionTimeHorizon,
			Integer lookback
	) {
	}

	private record TrainingBatchState(List<ChildTrainingTicket> tickets) {
		private TrainingBatchState {
			tickets = tickets == null ? List.of() : List.copyOf(tickets);
		}
	}

	private record ChildTrainingTicket(
			String ticketId,
			TrainingLaunchContext launchContext,
			Instant createdAt
	) {
	}

	private record ChildTicketPollResult(
			ChildTrainingTicket ticket,
			TrainingTicketSnapshot snapshot,
			TrainingJobStatus status,
			Integer progress
	) {
	}

	private record TrainingCompletedSnapshot(
			TrainingTicketSnapshot snapshot,
			TrainingLaunchContext launchContext
	) {
	}

	private record VariableCandidate(
			DigitalSubject subject,
			Variable variable
	) {
	}

}
