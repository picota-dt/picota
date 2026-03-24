package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.commands.real.state.DatasetStatisticsDelegate;
import io.picota.backend.control.commands.real.state.ModelProjectionDelegate;
import io.picota.backend.control.commands.real.state.TrainingOperationsDelegate;
import io.picota.backend.control.commands.real.state.TwinOperationsDelegate;
import io.picota.backend.control.training.ExternalTrainingClient;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.DigitalSubject;
import io.picota.backend.control.ui.schemas.DigitalTwin;
import io.picota.backend.control.ui.schemas.InferenceEngine;
import io.picota.backend.control.ui.schemas.RetrainingConfig;
import io.picota.backend.control.ui.schemas.SubjectDataset;
import io.picota.backend.control.ui.schemas.TrainingJob;
import io.picota.backend.control.ui.schemas.requests.*;
import io.picota.backend.control.ui.viewmodel.ModelViewMapper;
import io.picota.backend.model.*;
import io.picota.backend.persistence.DatasetStorage;
import io.picota.backend.persistence.FilesystemDatasetStorage;
import io.picota.backend.persistence.ModelPersistence;
import io.picota.backend.persistence.PersistenceException;

import java.nio.file.Path;
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
	private final ModelPersistence persistence;
	private final TwinOperationsDelegate twinOperationsDelegate;
	private final TrainingOperationsDelegate trainingOperationsDelegate;

	public RealCommandState() {
		this(null, TwinModelTemplate.defaultTemplate(), defaultDatasetStorage(), ExternalTrainingClient.disabled());
	}

	public RealCommandState(ModelPersistence persistence) {
		this(persistence, TwinModelTemplate.defaultTemplate(), defaultDatasetStorage(), ExternalTrainingClient.disabled());
	}

	public RealCommandState(ModelPersistence persistence, TwinModelTemplate twinModelTemplate) {
		this(persistence, twinModelTemplate, defaultDatasetStorage(), ExternalTrainingClient.disabled());
	}

	public RealCommandState(ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir) {
		this(persistence, twinModelTemplate, datasetsRootDir, ExternalTrainingClient.disabled());
	}

	public RealCommandState(
			ModelPersistence persistence,
			TwinModelTemplate twinModelTemplate,
			Path datasetsRootDir,
			ExternalTrainingClient trainingClient
	) {
		this(
				persistence,
				twinModelTemplate,
				new FilesystemDatasetStorage(datasetsRootDir == null ? defaultDatasetRootDir() : datasetsRootDir),
				trainingClient
		);
	}

	public RealCommandState(ModelPersistence persistence, TwinModelTemplate twinModelTemplate, DatasetStorage datasetStorage) {
		this(persistence, twinModelTemplate, datasetStorage, ExternalTrainingClient.disabled());
	}

	public RealCommandState(
			ModelPersistence persistence,
			TwinModelTemplate twinModelTemplate,
			DatasetStorage datasetStorage,
			ExternalTrainingClient trainingClient
	) {
		this.persistence = persistence;
		TwinModelTemplate template = twinModelTemplate == null ? TwinModelTemplate.defaultTemplate() : twinModelTemplate;
		DatasetStorage safeDatasetStorage = datasetStorage == null ? defaultDatasetStorage() : datasetStorage;
		ExternalTrainingClient safeTrainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
		ModelProjectionDelegate modelProjectionDelegate = new ModelProjectionDelegate(template);
		DatasetStatisticsDelegate datasetStatisticsDelegate = new DatasetStatisticsDelegate();
		Random sharedRandom = new Random();
		this.twinOperationsDelegate = new TwinOperationsDelegate(
				twinsByUser,
				trainingJobs,
				trainingJobOwnerById,
				new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
				sharedRandom,
				modelProjectionDelegate,
				datasetStatisticsDelegate,
				safeDatasetStorage,
				this::persistState
		);
		this.trainingOperationsDelegate = new TrainingOperationsDelegate(
				twinsByUser,
				trainingJobs,
				trainingJobOwnerById,
				safeDatasetStorage,
				safeTrainingClient,
				this::persistState
		);
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
		User user = new User(
				userId,
				name,
				normalizedEmail,
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
		ConcurrentMap<String, DigitalTwin> removedTwins = twinsByUser.remove(userId);
		if (removedTwins != null) {
			removedTwins.keySet().forEach(twinOperationsDelegate::deleteTwinDatasets);
		}
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
				current.avatarInitials(),
				current.credits() + creditsAdded,
				current.joinedAt()
		);
		usersById.put(userId, new StoredUser(updated, stored.password()));
		persistState();
		return new PurchaseCreditsResponse(updated.credits(), creditsAdded, "txn_" + shortId());
	}

	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return twinOperationsDelegate.listTwins(requireUserId(authToken), status, type, q, sort, order);
	}

	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return twinOperationsDelegate.createTwin(requireUserId(authToken), request);
	}

	public DigitalTwin getTwin(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return UiCommandFixtures.copyTwin(requireTwin(userId, twinId));
	}

	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.updateTwin(userId, requireTwin(userId, twinId), updates);
	}

	public void deleteTwin(String authToken, String twinId) {
		twinOperationsDelegate.deleteTwin(requireUserId(authToken), twinId);
	}

	public ModelContent getModel(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.getModel(requireTwin(userId, twinId));
	}

	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.saveModel(userId, twinId, requireTwin(userId, twinId), request);
	}

	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		String userId = requireUserId(authToken);
		requireTwin(userId, twinId);
		return twinOperationsDelegate.applyModelPrompt(request);
	}

	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.listSubjects(requireTwin(userId, twinId));
	}

	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.getSubject(requireTwin(userId, twinId), subjectId);
	}

	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.getSubjectTelemetry(requireTwin(userId, twinId), subjectId, historyPoints);
	}

	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.listDatasets(requireTwin(userId, twinId));
	}

	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.getDataset(requireTwin(userId, twinId), subjectId);
	}

	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		String userId = requireUserId(authToken);
		return twinOperationsDelegate.uploadDataset(userId, requireTwin(userId, twinId), subjectId, fileName, content);
	}

	public void deleteDataset(String authToken, String twinId, String subjectId) {
		String userId = requireUserId(authToken);
		twinOperationsDelegate.deleteDataset(userId, requireTwin(userId, twinId), subjectId);
	}

	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return trainingOperationsDelegate.getInferenceEngine(requireTwin(userId, twinId));
	}

	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		String userId = requireUserId(authToken);
		return trainingOperationsDelegate.saveEngineConfig(userId, requireTwin(userId, twinId), request);
	}

	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		String userId = requireUserId(authToken);
		return trainingOperationsDelegate.saveRetrainingConfig(userId, requireTwin(userId, twinId), request);
	}

	public TrainingJob launchTraining(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return trainingOperationsDelegate.launchTraining(userId, twinId, requireTwin(userId, twinId));
	}

	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		String userId = requireUserId(authToken);
		requireTwin(userId, twinId);
		return trainingOperationsDelegate.getTrainingJob(userId, twinId, jobId);
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

	private static String shortId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

	private static Path defaultDatasetRootDir() {
		return Path.of("./runtime/datasets").toAbsolutePath().normalize();
	}

	private static DatasetStorage defaultDatasetStorage() {
		return new FilesystemDatasetStorage(defaultDatasetRootDir());
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

	private record StoredUser(User user, String password) {
	}
}
