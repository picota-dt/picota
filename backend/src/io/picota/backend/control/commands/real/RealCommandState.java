package io.picota.backend.control.commands.real;

import io.picota.backend.control.auth.*;
import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.commands.UiCommandException;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.commands.real.state.DatasetStatisticsDelegate;
import io.picota.backend.control.commands.real.state.ModelProjectionDelegate;
import io.picota.backend.control.commands.real.state.TrainingOperationsDelegate;
import io.picota.backend.control.commands.real.state.TwinOperationsDelegate;
import io.picota.backend.control.ingestion.IngestMetricsRequest;
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
	private final ConcurrentMap<String, String> userIdByGoogleSubject = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> userIdByToken = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, ConcurrentMap<String, DigitalTwin>> twinsByUser = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, TrainingJob> trainingJobs = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> trainingJobOwnerById = new ConcurrentHashMap<>();
	private final ModelPersistence persistence;
	private final GoogleIdentityVerifier googleIdentityVerifier;
	private final TwinOperationsDelegate twinOperationsDelegate;
	private final TrainingOperationsDelegate trainingOperationsDelegate;

	public RealCommandState() {
		this(null, TwinModelTemplate.defaultTemplate(), defaultDatasetStorage(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public RealCommandState(ModelPersistence persistence) {
		this(persistence, TwinModelTemplate.defaultTemplate(), defaultDatasetStorage(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public RealCommandState(ModelPersistence persistence, TwinModelTemplate twinModelTemplate) {
		this(persistence, twinModelTemplate, defaultDatasetStorage(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
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
				trainingClient,
				defaultGoogleIdentityVerifier()
		);
	}

	public RealCommandState(ModelPersistence persistence, TwinModelTemplate twinModelTemplate, DatasetStorage datasetStorage) {
		this(persistence, twinModelTemplate, datasetStorage, ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public RealCommandState(
			ModelPersistence persistence,
			TwinModelTemplate twinModelTemplate,
			DatasetStorage datasetStorage,
			ExternalTrainingClient trainingClient,
			GoogleIdentityVerifier googleIdentityVerifier
	) {
		this.persistence = persistence;
		this.googleIdentityVerifier = googleIdentityVerifier == null ? defaultGoogleIdentityVerifier() : googleIdentityVerifier;
		TwinModelTemplate template = twinModelTemplate == null ? TwinModelTemplate.defaultTemplate() : twinModelTemplate;
		DatasetStorage safeDatasetStorage = datasetStorage == null ? defaultDatasetStorage() : datasetStorage;
		ExternalTrainingClient safeTrainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
		ModelProjectionDelegate modelProjectionDelegate = new ModelProjectionDelegate(template);
		DatasetStatisticsDelegate datasetStatisticsDelegate = new DatasetStatisticsDelegate();
		this.twinOperationsDelegate = new TwinOperationsDelegate(
				twinsByUser,
				trainingJobs,
				trainingJobOwnerById,
				new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
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

	public GoogleAuthConfigResponse getGoogleAuthConfig() {
		String clientId = googleIdentityVerifier.config() == null ? "" : googleIdentityVerifier.config().clientId();
		if (clientId == null || clientId.isBlank()) {
			throw new UiCommandException(503, "GOOGLE_AUTH_NOT_CONFIGURED", "Google authentication is not configured");
		}
		return new GoogleAuthConfigResponse(clientId);
	}

	public AuthResponse authenticateWithGoogle(GoogleAuthenticationRequest request) {
		validate(request != null && request.credential() != null && !request.credential().isBlank(),
				422,
				"VALIDATION_ERROR",
				"Google credential is required");
		final GoogleIdentity identity;
		try {
			identity = googleIdentityVerifier.verify(request.credential());
		} catch (GoogleIdentityVerificationException e) {
			throw new UiCommandException(401, "INVALID_GOOGLE_CREDENTIAL", e.getMessage());
		}

		String normalizedEmail = normalizeEmail(identity.email());
		validate(normalizedEmail != null, 422, "VALIDATION_ERROR", "Google account email is missing");

		String userId = userIdByGoogleSubject.get(identity.subject());
		StoredUser storedUser = userId == null ? null : usersById.get(userId);
		User nextUser;
		String nextUserId;
		if (storedUser == null) {
			String existingUserId = userIdByEmail.get(normalizedEmail);
			if (existingUserId != null) {
				storedUser = usersById.get(existingUserId);
				userId = existingUserId;
			}
		}
		if (storedUser == null) {
			nextUserId = "usr_" + shortId();
			String resolvedName = trimToNull(identity.name()) == null ? normalizedEmail : identity.name().trim();
			nextUser = new User(
					nextUserId,
					resolvedName,
					normalizedEmail,
					initials(resolvedName),
					1_000,
					Instant.now().toString()
			);
		} else {
			User current = storedUser.user();
			nextUserId = current.id();
			nextUser = new User(
					current.id(),
					current.name(),
					normalizedEmail,
					current.avatarInitials(),
					current.credits(),
					current.joinedAt()
			);
			String currentEmail = normalizeEmail(current.email());
			if (currentEmail != null && !currentEmail.equals(normalizedEmail)) {
				userIdByEmail.remove(currentEmail);
			}
		}
		usersById.put(nextUserId, new StoredUser(nextUser, identity.subject()));
		userIdByEmail.put(normalizedEmail, nextUserId);
		userIdByGoogleSubject.put(identity.subject(), nextUserId);
		twinsByUser.putIfAbsent(nextUserId, new ConcurrentHashMap<>());
		String token = issueToken(nextUserId);
		persistState();
		return new AuthResponse(token, 86_400, UiCommandFixtures.copyUser(nextUser));
	}

	public void logout(String authToken) {
		if (authToken == null || authToken.isBlank()) return;
		userIdByToken.remove(authToken);
		persistState();
	}

	public User getMe(String authToken) {
		return UiCommandFixtures.copyUser(requireStoredUser(requireUserId(authToken)).user());
	}

	public User updateMe(String authToken, UpdateUserRequest request) {
		String userId = requireUserId(authToken);
		StoredUser stored = requireStoredUser(userId);
		User current = stored.user();
		String requestedEmail = normalizeEmail(request == null ? null : request.email());
		String currentEmail = normalizeEmail(current.email());
		if (requestedEmail != null && currentEmail != null && !requestedEmail.equals(currentEmail)) {
			throw new UiCommandException(422, "EMAIL_READ_ONLY", "Email address is managed by Google and cannot be changed here");
		}
		User updated = new User(
				current.id(),
				coalesce(request == null ? null : request.name(), current.name()),
				current.email(),
				current.avatarInitials(),
				current.credits(),
				current.joinedAt()
		);
		usersById.put(userId, new StoredUser(updated, stored.googleSubject()));
		persistState();
		return UiCommandFixtures.copyUser(updated);
	}

	public void deleteMe(String authToken) {
		String userId = requireUserId(authToken);
		StoredUser removed = usersById.remove(userId);
		if (removed != null) {
			userIdByEmail.remove(removed.user().email().toLowerCase(Locale.ROOT));
			if (removed.googleSubject() != null && !removed.googleSubject().isBlank()) {
				userIdByGoogleSubject.remove(removed.googleSubject());
			}
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
		usersById.put(userId, new StoredUser(updated, stored.googleSubject()));
		persistState();
		return new PurchaseCreditsResponse(updated.credits(), creditsAdded, "txn_" + shortId());
	}

	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return twinOperationsDelegate.listTwins(requireUserId(authToken), status, type, q, sort, order);
	}

	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		String userId = requireUserId(authToken);
		DigitalTwin created = twinOperationsDelegate.createTwin(userId, request);
		ensureTwinIngestionToken(userId, created.id());
		return UiCommandFixtures.copyTwin(requireTwin(userId, created.id()));
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

	public IngestionToken getTwinIngestionToken(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return new IngestionToken(ensureTwinIngestionToken(userId, twinId));
	}

	public IngestionToken rotateTwinIngestionToken(String authToken, String twinId) {
		String userId = requireUserId(authToken);
		return new IngestionToken(rotateTwinIngestionTokenInternal(userId, twinId));
	}

	public void ingestSubjectSensorMetrics(String authToken, String twinId, String subjectId, IngestMetricsRequest request) {
		TwinOwnerAndTwin ownerAndTwin = requireTwinById(twinId);
		requireMatchingTwinIngestionToken(ownerAndTwin.twin(), authToken);
		twinOperationsDelegate.ingestSubjectSensorMetrics(ownerAndTwin.twin(), subjectId, request);
		trainingOperationsDelegate.inferSubjectFromLatestCompletedTraining(ownerAndTwin.ownerUserId(), ownerAndTwin.twin(), subjectId);
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

	private TwinOwnerAndTwin requireTwinById(String twinId) {
		if (twinId == null || twinId.isBlank()) {
			throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
		}
		for (Map.Entry<String, ConcurrentMap<String, DigitalTwin>> entry : twinsByUser.entrySet()) {
			DigitalTwin twin = entry.getValue() == null ? null : entry.getValue().get(twinId);
			if (twin != null) return new TwinOwnerAndTwin(entry.getKey(), twin);
		}
		throw new UiCommandException(404, "TWIN_NOT_FOUND", "No twin found with id " + twinId);
	}

	private String ensureTwinIngestionToken(String userId, String twinId) {
		DigitalTwin twin = requireTwin(userId, twinId);
		String current = trimToNull(twin.ingestionToken());
		if (current != null) return current;
		String generated = newTwinIngestionToken();
		upsertTwinIngestionToken(userId, twin, generated);
		persistState();
		return generated;
	}

	private String rotateTwinIngestionTokenInternal(String userId, String twinId) {
		DigitalTwin twin = requireTwin(userId, twinId);
		String generated = newTwinIngestionToken();
		upsertTwinIngestionToken(userId, twin, generated);
		persistState();
		return generated;
	}

	private void requireMatchingTwinIngestionToken(DigitalTwin twin, String providedToken) {
		String expected = trimToNull(twin == null ? null : twin.ingestionToken());
		String provided = trimToNull(providedToken);
		if (expected == null || provided == null || !expected.equals(provided)) {
			throw new UiCommandException(401, "UNAUTHORIZED", "Ingestion token is missing or invalid");
		}
	}

	private void upsertTwinIngestionToken(String userId, DigitalTwin twin, String token) {
		DigitalTwin updated = withIngestionToken(twin, token);
		twinsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>()).put(updated.id(), updated);
	}

	private static DigitalTwin withIngestionToken(DigitalTwin twin, String token) {
		return new DigitalTwin(
				twin.id(),
				twin.name(),
				twin.description(),
				twin.version(),
				twin.image(),
				twin.type(),
				twin.status(),
				twin.updatedAt(),
				twin.creditsUsed(),
				twin.model(),
				twin.subjects(),
				twin.inferenceEngine(),
				twin.datasets(),
				token
		);
	}

	private boolean loadPersistedState() {
		if (persistence == null) return false;
		Application model = persistence.loadModel().orElse(null);
		if (model == null) return false;

		usersById.clear();
		userIdByEmail.clear();
		userIdByGoogleSubject.clear();
		userIdByToken.clear();
		twinsByUser.clear();
		trainingJobs.clear();
		trainingJobOwnerById.clear();

		for (UserAccount account : model.users()) {
			if (account == null || account.id() == null) continue;
			User uiUser = ModelViewMapper.toViewUser(account);
			usersById.put(account.id(), new StoredUser(uiUser, account.googleSubject()));
			if (uiUser.email() != null) {
				userIdByEmail.put(uiUser.email().toLowerCase(Locale.ROOT), account.id());
			}
			if (account.googleSubject() != null && !account.googleSubject().isBlank()) {
				userIdByGoogleSubject.put(account.googleSubject(), account.id());
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
				.map(stored -> ModelViewMapper.toDomainUserAccount(stored.user(), stored.googleSubject()))
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

	private static GoogleIdentityVerifier defaultGoogleIdentityVerifier() {
		return new StubGoogleIdentityVerifier(new GoogleAuthConfig("demo-google-client-id"), null);
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

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String newTwinIngestionToken() {
		return "itok_" + UUID.randomUUID().toString().replace("-", "");
	}

	private record StoredUser(User user, String googleSubject) {
	}

	private record TwinOwnerAndTwin(String ownerUserId, DigitalTwin twin) {
	}
}
