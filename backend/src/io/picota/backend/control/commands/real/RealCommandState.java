package io.picota.backend.control.commands.real;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.DigitalSubject;
import io.picota.backend.control.ui.schemas.DigitalTwin;
import io.picota.backend.control.ui.schemas.InferenceEngine;
import io.picota.backend.control.ui.schemas.InferredVariableResult;
import io.picota.backend.control.ui.schemas.RetrainingConfig;
import io.picota.backend.control.ui.schemas.RetrainingSchedule;
import io.picota.backend.control.ui.schemas.SubjectDataset;
import io.picota.backend.control.ui.schemas.TrainingAlgorithm;
import io.picota.backend.control.ui.schemas.TrainingJob;
import io.picota.backend.control.ui.schemas.TrainingJobStatus;
import io.picota.backend.control.ui.schemas.TwinStatus;
import io.picota.backend.control.ui.schemas.TwinType;
import io.picota.backend.control.ui.schemas.Variable;
import io.picota.backend.control.ui.schemas.VariableStat;
import io.picota.backend.control.ui.schemas.VariableType;
import io.picota.backend.control.ui.schemas.requests.*;
import io.picota.backend.control.ui.viewmodel.ModelViewMapper;
import io.picota.backend.model.*;
import io.picota.backend.persistence.ModelPersistence;
import io.picota.backend.persistence.PersistenceException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RealCommandState {
	private final ConcurrentMap<String, StoredUser> usersById = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> userIdByEmail = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> userIdByToken = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, TrainingJob> trainingJobs = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> trainingJobOwnerById = new ConcurrentHashMap<>();
	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
	private final Random random = new Random();
	private final ModelPersistence persistence;

	public RealCommandState() {
		this(null);
	}

	public RealCommandState(ModelPersistence persistence) {
		this.persistence = persistence;
		if (!loadPersistedState()) {
			// Real mode starts empty when there is no persisted state.
			persistState();
		}
	}

	public AuthResponse register(RegisterRequest request) {
		validate(request != null && request.email() != null && !request.email().isBlank(), 422, "VALIDATION_ERROR", "Email is required");
		validate(request.password() != null && request.password().length() >= 8, 422, "VALIDATION_ERROR", "Password must be at least 8 characters");
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		if (userIdByEmail.containsKey(normalizedEmail)) {
			throw new UiCommandException(409, "EMAIL_ALREADY_EXISTS", "An account with this email already exists");
		}

		String userId = "usr_" + shortId();
		String name = request.name() == null || request.name().isBlank() ? "New User" : request.name().trim();
		String organization = request.organization() == null || request.organization().isBlank()
				? "Picota Organization"
				: request.organization().trim();
		User user = new User(
				userId,
				name,
				normalizedEmail,
				"Engineer",
				organization,
				initials(name),
				1_000,
				Instant.now().toString()
		);
		usersById.put(userId, new StoredUser(user, request.password()));
		userIdByEmail.put(normalizedEmail, userId);
		twinsByUser.putIfAbsent(userId, new ConcurrentHashMap<>());
		String token = issueToken(userId);
		persistState();
		return new AuthResponse(token, 86_400, UiCommandFixtures.copyUser(user));
	}

	public AuthResponse login(LoginRequest request) {
		validate(request != null && request.email() != null && request.password() != null, 422, "VALIDATION_ERROR", "Email and password are required");
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		String userId = userIdByEmail.get(normalizedEmail);
		if (userId == null) {
			throw new UiCommandException(401, "INVALID_CREDENTIALS", "Email or password is incorrect");
		}
		StoredUser storedUser = usersById.get(userId);
		if (storedUser == null || !storedUser.password().equals(request.password())) {
			throw new UiCommandException(401, "INVALID_CREDENTIALS", "Email or password is incorrect");
		}
		String token = issueToken(userId);
		persistState();
		return new AuthResponse(token, 86_400, UiCommandFixtures.copyUser(storedUser.user()));
	}

	public void logout(String authToken) {
		if (authToken == null || authToken.isBlank()) return;
		userIdByToken.remove(authToken);
		persistState();
	}

	public void changePassword(String authToken, ChangePasswordRequest request) {
		String userId = requireUserId(authToken);
		validate(request != null && request.currentPassword() != null && request.newPassword() != null, 422, "VALIDATION_ERROR", "Both passwords are required");
		validate(request.newPassword().length() >= 8, 422, "VALIDATION_ERROR", "Password must be at least 8 characters");
		StoredUser stored = requireStoredUser(userId);
		if (!stored.password().equals(request.currentPassword())) {
			throw new UiCommandException(422, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
		}
		usersById.put(userId, new StoredUser(stored.user(), request.newPassword()));
		persistState();
	}

	public User getMe(String authToken) {
		return UiCommandFixtures.copyUser(requireStoredUser(requireUserId(authToken)).user());
	}

	public User updateMe(String authToken, UpdateUserRequest request) {
		String userId = requireUserId(authToken);
		StoredUser stored = requireStoredUser(userId);
		User current = stored.user();
		User updated = new User(
				current.id(),
				coalesce(request == null ? null : request.name(), current.name()),
				normalizeEmail(coalesce(request == null ? null : request.email(), current.email())),
				current.role(),
				coalesce(request == null ? null : request.organization(), current.organization()),
				current.avatarInitials(),
				current.credits(),
				current.joinedAt()
		);
		if (!current.email().equalsIgnoreCase(updated.email())) {
			String existing = userIdByEmail.get(updated.email().toLowerCase(Locale.ROOT));
			if (existing != null && !existing.equals(userId)) {
				throw new UiCommandException(409, "EMAIL_ALREADY_EXISTS", "An account with this email already exists");
			}
			userIdByEmail.remove(current.email().toLowerCase(Locale.ROOT));
			userIdByEmail.put(updated.email().toLowerCase(Locale.ROOT), userId);
		}
		usersById.put(userId, new StoredUser(updated, stored.password()));
		persistState();
		return UiCommandFixtures.copyUser(updated);
	}

	public void deleteMe(String authToken) {
		String userId = requireUserId(authToken);
		StoredUser removed = usersById.remove(userId);
		if (removed != null) {
			userIdByEmail.remove(removed.user().email().toLowerCase(Locale.ROOT));
		}
		twinsByUser.remove(userId);
		userIdByToken.entrySet().removeIf(e -> e.getValue().equals(userId));
		trainingJobs.entrySet().removeIf(e -> userId.equals(trainingJobOwnerById.get(e.getKey())));
		trainingJobOwnerById.entrySet().removeIf(e -> userId.equals(e.getValue()));
		persistState();
	}

	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		String userId = requireUserId(authToken);
		CreditPack pack = request == null ? null : request.pack();
		validate(pack != null, 422, "VALIDATION_ERROR", "Credit pack is required");
		int creditsAdded = switch (pack) {
			case STARTER -> 500;
			case GROWTH -> 2_000;
			case SCALE -> 10_000;
		};

		StoredUser stored = requireStoredUser(userId);
		User current = stored.user();
		User updated = new User(
				current.id(),
				current.name(),
				current.email(),
				current.role(),
				current.organization(),
				current.avatarInitials(),
				current.credits() + creditsAdded,
				current.joinedAt()
		);
		usersById.put(userId, new StoredUser(updated, stored.password()));
		persistState();
		return new PurchaseCreditsResponse(updated.credits(), creditsAdded, "txn_" + shortId());
	}

	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		String userId = requireUserId(authToken);
		List<DigitalTwin> twins = new ArrayList<>(twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).values());

		if (status != null && !status.isBlank()) {
			TwinStatus filterStatus = TwinStatus.fromWireValue(status);
			twins = twins.stream().filter(t -> t.status() == filterStatus).toList();
		}
		if (type != null && !type.isBlank()) {
			TwinType filterType = TwinType.fromWireValue(type);
			twins = twins.stream().filter(t -> t.type() == filterType).toList();
		}
		if (q != null && !q.isBlank()) {
			String query = q.toLowerCase(Locale.ROOT);
			twins = twins.stream().filter(t ->
					t.name().toLowerCase(Locale.ROOT).contains(query) ||
							t.description().toLowerCase(Locale.ROOT).contains(query)
			).toList();
		}

		Comparator<DigitalTwin> comparator = switch (sort == null ? "updatedAt" : sort) {
			case "name" -> Comparator.comparing(DigitalTwin::name, String.CASE_INSENSITIVE_ORDER);
			case "creditsUsed" -> Comparator.comparingInt(DigitalTwin::creditsUsed);
			default -> Comparator.comparing(DigitalTwin::updatedAt, String.CASE_INSENSITIVE_ORDER);
		};
		boolean desc = !"asc".equalsIgnoreCase(order);
		if (desc) comparator = comparator.reversed();
		twins = twins.stream().sorted(comparator).map(UiCommandFixtures::copyTwin).toList();
		return twins;
	}

	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		String userId = requireUserId(authToken);
		validate(request != null && request.name() != null && !request.name().isBlank(), 422, "VALIDATION_ERROR", "Twin name is required");
		validate(request.type() != null, 422, "VALIDATION_ERROR", "Twin type is required");

		DigitalTwin twin = new DigitalTwin(
				"twin_" + shortId(),
				request.name().trim(),
				request.description() == null || request.description().isBlank() ? "No description provided." : request.description().trim(),
				"0.1.0",
				"https://images.unsplash.com/photo-1647427060118-4911c9821b82?auto=format&fit=crop&w=1080&q=80",
				request.type(),
				TwinStatus.DRAFT,
				"Just now",
				0,
				"# " + request.name().trim() + " — Digital Twin Model\nsubjects: []\nconstraints: []\n",
				List.of(),
				null,
				List.of()
		);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(twin.id(), twin);
		persistState();
		return UiCommandFixtures.copyTwin(twin);
	}

	public DigitalTwin getTwin(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return UiCommandFixtures.copyTwin(requireTwin(userId, twinId));
	}

	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		String userId = requireUserId(authToken);
		DigitalTwin current = requireTwin(userId, twinId);
		Map<String, Object> safeUpdates = updates == null ? Map.of() : updates;

		DigitalTwin updated = new DigitalTwin(
				current.id(),
				stringOrDefault(safeUpdates.get("name"), current.name()),
				stringOrDefault(safeUpdates.get("description"), current.description()),
				stringOrDefault(safeUpdates.get("version"), current.version()),
				stringOrDefault(safeUpdates.get("image"), current.image()),
				enumOrDefault(safeUpdates.get("type"), TwinType.class, current.type()),
				enumOrDefault(safeUpdates.get("status"), TwinStatus.class, current.status()),
				stringOrDefault(safeUpdates.get("updatedAt"), "Just now"),
				intOrDefault(safeUpdates.get("creditsUsed"), current.creditsUsed()),
				stringOrDefault(safeUpdates.get("model"), current.model()),
				listOrDefault(safeUpdates.get("subjects"), new TypeReference<List<DigitalSubject>>() {
				}, current.subjects()),
				objectOrDefault(safeUpdates.get("inferenceEngine"), InferenceEngine.class, current.inferenceEngine()),
				listOrDefault(safeUpdates.get("datasets"), new TypeReference<List<SubjectDataset>>() {
				}, current.datasets())
		);

		twinsByUser.get(userId).put(updated.id(), updated);
		persistState();
		return UiCommandFixtures.copyTwin(updated);
	}

	public void deleteTwin(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		ConcurrentMap<String, DigitalTwin> twins = twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
		DigitalTwin removed = twins.remove(twinId);
		if (removed == null) throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
		trainingJobs.entrySet().removeIf(e -> twinId.equals(e.getValue().twinId()));
		trainingJobOwnerById.entrySet().removeIf(e -> !trainingJobs.containsKey(e.getKey()));
		persistState();
	}

	public ModelContent getModel(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		return new ModelContent(twin.model(), twin.version());
	}

	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		validate(request != null && request.content() != null, 422, "VALIDATION_ERROR", "Model content is required");
		String newVersion = bumpVersion(twin.version(), request.versionBump() == null ? VersionBump.PATCH : request.versionBump());
		DigitalTwin updated = new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				newVersion,
				twin.image(),
				twin.type(),
				twin.status(),
				"Just now",
				twin.creditsUsed(),
				request.content(),
				twin.subjects(),
				twin.inferenceEngine(),
				twin.datasets()
		);
		twinsByUser.get(userId).put(twinId, updated);
		persistState();
		return new SaveModelResponse(newVersion, Instant.now());
	}

	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		String userId = requireUserId(authToken);
		requireTwin(userId, twinId);
		String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
		String base = request == null || request.currentContent() == null ? "" : request.currentContent();
		String updated = base + "\n# AI suggestion: " + (prompt.isBlank() ? "No prompt provided" : prompt);
		return new ApplyModelPromptResponse(updated);
	}

	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return requireTwin(userId, twinId).subjects().stream().map(UiCommandFixtures::copySubject).toList();
	}

	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		return twin.subjects().stream()
				.filter(s -> s.id().equals(subjectId))
				.findFirst()
				.map(UiCommandFixtures::copySubject)
				.orElseThrow(() -> new UiCommandException(404, "SUBJECT_NOT_FOUND", "No subject found with id " + subjectId));
	}

	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		DigitalSubject subject = getSubject(authToken, twinId, subjectId);
		return UiCommandFixtures.telemetryForSubject(subject, historyPoints, random);
	}

	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return requireTwin(userId, twinId).datasets().stream().map(UiCommandFixtures::copyDataset).toList();
	}

	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		return twin.datasets().stream()
				.filter(d -> d.subjectId().equals(subjectId))
				.findFirst()
				.map(UiCommandFixtures::copyDataset)
				.orElseThrow(() -> new UiCommandException(404, "DATASET_NOT_FOUND", "No dataset found for subject " + subjectId));
	}

	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		DigitalSubject subject = twin.subjects().stream()
				.filter(s -> s.id().equals(subjectId))
				.findFirst()
				.orElseThrow(() -> new UiCommandException(404, "SUBJECT_NOT_FOUND", "No subject found with id " + subjectId));

		CsvStats stats = computeCsvStats(subject, content == null ? new byte[0] : content);
		SubjectDataset dataset = new SubjectDataset(
				subjectId,
				fileName,
				stats.rowCount(),
				0,
				Instant.now(),
				stats.variableStats()
		);
		List<SubjectDataset> updatedDatasets = new ArrayList<>(twin.datasets());
		updatedDatasets.removeIf(d -> d.subjectId().equals(subjectId));
		updatedDatasets.add(dataset);
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
				twin.inferenceEngine(),
				updatedDatasets
		);
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistState();
		return UiCommandFixtures.copyDataset(dataset);
	}

	public void deleteDataset(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		List<SubjectDataset> updatedDatasets = new ArrayList<>(twin.datasets());
		boolean removed = updatedDatasets.removeIf(d -> d.subjectId().equals(subjectId));
		if (!removed)
			throw new UiCommandException(404, "DATASET_NOT_FOUND", "No dataset found for subject " + subjectId);
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
				twin.inferenceEngine(),
				updatedDatasets
		);
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistState();
	}

	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		return twin.inferenceEngine() == null ? null : UiCommandFixtures.copyInferenceEngine(twin.inferenceEngine());
	}

	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine current = twin.inferenceEngine();
		InferenceEngine next = new InferenceEngine(
				request != null && request.trained() != null
						? request.trained()
						: current != null && current.trained(),
				request != null && request.algorithm() != null
						? request.algorithm()
						: current != null && current.algorithm() != null ? current.algorithm() : TrainingAlgorithm.LSTM,
				request != null ? request.trainedAt() : current == null ? null : current.trainedAt(),
				request != null && request.epochs() != null
						? request.epochs()
						: current == null ? 100 : current.epochs(),
				request != null && request.learningRate() != null
						? request.learningRate()
						: current == null ? 0.001 : current.learningRate(),
				request != null && request.windowSize() != null
						? request.windowSize()
						: current == null ? 60 : current.windowSize(),
				request != null && request.batchSize() != null
						? request.batchSize()
						: current == null ? 32 : current.batchSize(),
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
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistState();
		return UiCommandFixtures.copyInferenceEngine(next);
	}

	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine current = twin.inferenceEngine() == null
				? new InferenceEngine(false, TrainingAlgorithm.LSTM, null, 100, 0.001, 60, 32, List.of(), null)
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
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		persistState();
		return retraining;
	}

	public TrainingJob launchTraining(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		DigitalTwin twin = requireTwin(userId, twinId);
		validate(twin.inferenceEngine() != null, 422, "PRECONDITION_FAILED", "Inference engine is not configured");
		validate(twin.datasets().stream().anyMatch(d -> d.uploadedRecords() != null && d.uploadedRecords() > 0),
				422,
				"PRECONDITION_FAILED",
				"At least one subject must have an uploaded dataset before training");

		boolean hasRunning = trainingJobs.values().stream()
				.anyMatch(job -> job.twinId().equals(twinId) &&
						(job.status() == TrainingJobStatus.QUEUED
								|| job.status() == TrainingJobStatus.PREPARING
								|| job.status() == TrainingJobStatus.TRAINING
								|| job.status() == TrainingJobStatus.EVALUATING));
		if (hasRunning) {
			throw new UiCommandException(409, "TRAINING_ALREADY_RUNNING", "A training job is already in progress for this twin");
		}

		TrainingJob job = new TrainingJob(
				"job_" + shortId(),
				twinId,
				TrainingJobStatus.QUEUED,
				0,
				"Queued",
				Instant.now(),
				null,
				null,
				null,
				null
		);
		trainingJobs.put(job.jobId(), job);
		trainingJobOwnerById.put(job.jobId(), userId);
		persistState();
		return job;
	}

	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		String userId = requireUserId(authToken);
		requireTwin(userId, twinId);
		TrainingJob current = trainingJobs.get(jobId);
		if (current == null || !current.twinId().equals(twinId)) {
			throw new UiCommandException(404, "TRAINING_JOB_NOT_FOUND", "No training job found with id " + jobId);
		}
		if (current.status() == TrainingJobStatus.DONE || current.status() == TrainingJobStatus.FAILED) {
			return current;
		}

		int nextProgress = Math.min((current.progress() == null ? 0 : current.progress()) + 25, 100);
		TrainingJobStatus nextStatus;
		String phase;
		Instant startedAt = current.startedAt() == null ? Instant.now() : current.startedAt();
		Instant completedAt = null;
		InferenceEngine result = null;

		if (nextProgress < 10) {
			nextStatus = TrainingJobStatus.PREPARING;
			phase = "Preparing dataset…";
		} else if (nextProgress < 75) {
			nextStatus = TrainingJobStatus.TRAINING;
			phase = "Training in progress…";
		} else if (nextProgress < 100) {
			nextStatus = TrainingJobStatus.EVALUATING;
			phase = "Evaluating model…";
		} else {
			nextStatus = TrainingJobStatus.DONE;
			phase = "Training complete";
			completedAt = Instant.now();
			result = finalizeTraining(userId, twinId);
		}

		TrainingJob updated = new TrainingJob(
				current.jobId(),
				current.twinId(),
				nextStatus,
				nextProgress,
				phase,
				current.createdAt(),
				startedAt,
				completedAt,
				null,
				result
		);
		trainingJobs.put(updated.jobId(), updated);
		persistState();
		return updated;
	}

	protected String requireUserId(String authToken) {
		if (authToken == null || authToken.isBlank()) {
			throw new UiCommandException(401, "UNAUTHORIZED", "Bearer token is missing or has expired");
		}
		String userId = userIdByToken.get(authToken);
		if (userId == null) {
			throw new UiCommandException(401, "UNAUTHORIZED", "Bearer token is missing or has expired");
		}
		return userId;
	}

	private InferenceEngine finalizeTraining(String userId, String twinId) {
		DigitalTwin twin = requireTwin(userId, twinId);
		InferenceEngine engine = twin.inferenceEngine();
		List<InferredVariableResult> inferred = twin.subjects().stream()
				.flatMap(s -> s.variables().stream())
				.filter(v -> v.variableType() == VariableType.INFERRED)
				.map(v -> new InferredVariableResult(
						v.name(),
						88 + random.nextDouble() * 10,
						0.1 + random.nextDouble(),
						0.5 + random.nextDouble() * 4
				))
				.toList();

		InferenceEngine trained = new InferenceEngine(
				true,
				engine.algorithm(),
				Instant.now(),
				engine.epochs(),
				engine.learningRate(),
				engine.windowSize(),
				engine.batchSize(),
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
		twinsByUser.get(userId).put(twin.id(), updatedTwin);
		return trained;
	}

	private String issueToken(String userId) {
		String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
		userIdByToken.put(token, userId);
		return token;
	}

	private StoredUser requireStoredUser(String userId) {
		StoredUser stored = usersById.get(userId);
		if (stored == null) throw new UiCommandException(404, "USER_NOT_FOUND", "User does not exist");
		return stored;
	}

	private DigitalTwin requireTwin(String userId, String twinId) {
		DigitalTwin twin = twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).get(twinId);
		if (twin == null) throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
		return twin;
	}

	private boolean loadPersistedState() {
		if (persistence == null) return false;
		Application model = persistence.loadModel().orElse(null);
		if (model == null) return false;

		usersById.clear();
		userIdByEmail.clear();
		userIdByToken.clear();
		twinsByUser.clear();
		trainingJobs.clear();
		trainingJobOwnerById.clear();

		for (UserAccount account : model.users()) {
			if (account == null || account.id() == null) continue;
			User uiUser = ModelViewMapper.toViewUser(account);
			usersById.put(account.id(), new StoredUser(uiUser, account.passwordHash()));
			if (uiUser.email() != null) {
				userIdByEmail.put(uiUser.email().toLowerCase(Locale.ROOT), account.id());
			}
		}
		for (UserSession session : model.sessions()) {
			if (session == null || session.token() == null || session.userId() == null) continue;
			userIdByToken.put(session.token(), session.userId());
		}
		for (TwinAggregate aggregate : model.twins()) {
			if (aggregate == null || aggregate.ownerUserId() == null || aggregate.twin() == null) continue;
			twinsByUser.computeIfAbsent(aggregate.ownerUserId(), ignored -> new ConcurrentHashMap<>())
					.put(aggregate.twin().id(), ModelViewMapper.toViewTwin(aggregate.twin()));
		}
		for (TrainingJobAggregate aggregate : model.trainingJobs()) {
			if (aggregate == null || aggregate.job() == null || aggregate.job().jobId() == null) continue;
			trainingJobs.put(aggregate.job().jobId(), ModelViewMapper.toViewTrainingJob(aggregate.job()));
			if (aggregate.ownerUserId() != null) {
				trainingJobOwnerById.put(aggregate.job().jobId(), aggregate.ownerUserId());
			}
		}
		return true;
	}

	private void persistState() {
		if (persistence == null) return;
		try {
			persistence.saveModel(exportModel());
		} catch (PersistenceException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new PersistenceException("Unable to persist application state", e);
		}
	}

	private Application exportModel() {
		List<UserAccount> users = usersById.values().stream()
				.map(stored -> ModelViewMapper.toDomainUserAccount(stored.user(), stored.password()))
				.sorted(Comparator.comparing(UserAccount::id))
				.toList();

		List<UserSession> sessions = userIdByToken.entrySet().stream()
				.map(entry -> new UserSession(entry.getKey(), entry.getValue(), Instant.now()))
				.sorted(Comparator.comparing(UserSession::token))
				.toList();

		List<TwinAggregate> twins = twinsByUser.entrySet().stream()
				.flatMap(ownerEntry -> ownerEntry.getValue().values().stream()
						.map(twin -> new TwinAggregate(ownerEntry.getKey(), ModelViewMapper.toDomainTwin(twin))))
				.sorted(Comparator.comparing(aggregate -> aggregate.twin().id()))
				.toList();

		List<TrainingJobAggregate> jobs = trainingJobs.values().stream()
				.map(job -> new TrainingJobAggregate(trainingJobOwnerById.get(job.jobId()), ModelViewMapper.toDomainTrainingJob(job)))
				.sorted(Comparator.comparing(aggregate -> aggregate.job().jobId()))
				.toList();

		return new Application(users, sessions, twins, jobs);
	}

	private CsvStats computeCsvStats(DigitalSubject subject, byte[] content) {
		if (content.length == 0) {
			Map<String, VariableStat> generated = generateVariableStatsFromSubject(subject, 500);
			return new CsvStats(500, generated);
		}

		String text = new String(content, StandardCharsets.UTF_8).trim();
		if (text.isEmpty()) {
			Map<String, VariableStat> generated = generateVariableStatsFromSubject(subject, 500);
			return new CsvStats(500, generated);
		}

		String[] lines = text.split("\\r?\\n");
		if (lines.length < 2) {
			Map<String, VariableStat> generated = generateVariableStatsFromSubject(subject, 500);
			return new CsvStats(500, generated);
		}

		String[] headers = lines[0].split(",");
		List<double[]> rows = new ArrayList<>();
		for (int i = 1; i < lines.length; i++) {
			String[] cells = lines[i].split(",");
			if (cells.length != headers.length) continue;
			double[] parsed = new double[cells.length];
			boolean ok = true;
			for (int j = 0; j < cells.length; j++) {
				try {
					parsed[j] = Double.parseDouble(cells[j].trim());
				} catch (NumberFormatException ex) {
					ok = false;
					break;
				}
			}
			if (ok) rows.add(parsed);
		}

		Map<String, VariableStat> stats = new LinkedHashMap<>();
		for (int col = 0; col < headers.length; col++) {
			String header = headers[col].trim().replace("\"", "");
			List<Double> values = new ArrayList<>();
			for (double[] row : rows) values.add(row[col]);
			if (values.isEmpty()) continue;
			stats.put(header, computeStats(values));
		}
		if (stats.isEmpty()) {
			stats = generateVariableStatsFromSubject(subject, rows.isEmpty() ? 500 : rows.size());
		}
		int count = rows.isEmpty() ? 500 : rows.size();
		return new CsvStats(count, stats);
	}

	private Map<String, VariableStat> generateVariableStatsFromSubject(DigitalSubject subject, int count) {
		Map<String, VariableStat> generated = new LinkedHashMap<>();
		for (Variable variable : subject.variables()) {
			double base = variable.value() == null ? 0.0 : variable.value();
			double std = Math.max(Math.abs(base) * 0.05, 0.1);
			generated.put(variable.name(), new VariableStat(
					count,
					round(base),
					round(std),
					round(base - 2 * std),
					round(base + 2 * std),
					round(base)
			));
		}
		return generated;
	}

	private VariableStat computeStats(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		int count = values.size();
		double sum = values.stream().reduce(0.0, Double::sum);
		double mean = sum / count;
		double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / count;
		double std = Math.sqrt(variance);
		double min = sorted.get(0);
		double max = sorted.get(sorted.size() - 1);
		double median = sorted.size() % 2 == 0
				? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
				: sorted.get(sorted.size() / 2);
		return new VariableStat(count, round(mean), round(std), round(min), round(max), round(median));
	}

	private String bumpVersion(String version, VersionBump bump) {
		String[] parts = (version == null ? "0.1.0" : version).split("\\.");
		int major = parts.length > 0 ? parseInt(parts[0], 0) : 0;
		int minor = parts.length > 1 ? parseInt(parts[1], 1) : 1;
		int patch = parts.length > 2 ? parseInt(parts[2], 0) : 0;
		return switch (bump) {
			case MAJOR -> (major + 1) + ".0.0";
			case MINOR -> major + "." + (minor + 1) + ".0";
			case PATCH -> major + "." + minor + "." + (patch + 1);
		};
	}

	private static String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	private static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private static String coalesce(String candidate, String fallback) {
		return candidate == null || candidate.isBlank() ? fallback : candidate.trim();
	}

	private static String initials(String fullName) {
		String[] parts = fullName.trim().split("\\s+");
		if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
		return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
	}

	private static void validate(boolean condition, int status, String code, String message) {
		if (!condition) throw new UiCommandException(status, code, message);
	}

	private String stringOrDefault(Object raw, String fallback) {
		if (raw == null) return fallback;
		String value = String.valueOf(raw);
		return value.isBlank() ? fallback : value;
	}

	private int intOrDefault(Object raw, int fallback) {
		if (raw == null) return fallback;
		if (raw instanceof Number number) return number.intValue();
		try {
			return Integer.parseInt(String.valueOf(raw));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private <T> T enumOrDefault(Object raw, Class<T> type, T fallback) {
		if (raw == null) return fallback;
		return mapper.convertValue(raw, type);
	}

	private <T> T objectOrDefault(Object raw, Class<T> type, T fallback) {
		if (raw == null) return fallback;
		return mapper.convertValue(raw, type);
	}

	private <T> List<T> listOrDefault(Object raw, TypeReference<List<T>> typeRef, List<T> fallback) {
		if (raw == null) return fallback;
		List<T> converted = mapper.convertValue(raw, typeRef);
		return converted == null ? fallback : converted;
	}

	private static double round(double value) {
		return Math.round(value * 1_000.0) / 1_000.0;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private record StoredUser(User user, String password) {
	}

	private record CsvStats(int rowCount, Map<String, VariableStat> variableStats) {
	}
}
