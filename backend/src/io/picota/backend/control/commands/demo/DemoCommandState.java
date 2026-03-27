package io.picota.backend.control.commands.demo;

import io.picota.backend.control.auth.GoogleAuthConfig;
import io.picota.backend.control.auth.GoogleIdentity;
import io.picota.backend.control.auth.GoogleIdentityVerifier;
import io.picota.backend.control.auth.StubGoogleIdentityVerifier;
import io.picota.backend.control.commands.TwinModelTemplate;
import io.picota.backend.control.commands.UiCommandFixtures;
import io.picota.backend.control.commands.real.RealCommandState;
import io.picota.backend.control.ingestion.IngestMetricsRequest;
import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.requests.*;
import io.picota.backend.control.ui.viewmodel.ModelViewMapper;
import io.picota.backend.model.Application;
import io.picota.backend.model.TwinAggregate;
import io.picota.backend.model.UserAccount;
import io.picota.backend.persistence.DatasetStorage;
import io.picota.backend.persistence.ModelPersistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DemoCommandState {
	private final RealCommandState delegate;
	private final String demoToken;
	private final GoogleIdentityVerifier googleIdentityVerifier;

	public DemoCommandState() {
		this(TwinModelTemplate.defaultTemplate(), defaultGoogleIdentityVerifier());
	}

	public DemoCommandState(TwinModelTemplate twinModelTemplate) {
		this(twinModelTemplate, defaultGoogleIdentityVerifier());
	}

	public DemoCommandState(TwinModelTemplate twinModelTemplate, GoogleIdentityVerifier googleIdentityVerifier) {
		this.googleIdentityVerifier = googleIdentityVerifier == null ? defaultGoogleIdentityVerifier() : googleIdentityVerifier;
		this.delegate = new RealCommandState(
				new InMemoryModelPersistence(createDemoModel()),
				twinModelTemplate,
				DatasetStorage.noOp(),
				io.picota.backend.control.training.ExternalTrainingClient.disabled(),
				this.googleIdentityVerifier
		);
		this.demoToken = delegate.authenticateWithGoogle(new GoogleAuthenticationRequest("demo-google-credential")).token();
	}

	public void logout(String authToken) {
	}

	public GoogleAuthConfigResponse getGoogleAuthConfig() {
		String clientId = googleIdentityVerifier.config() == null ? "" : googleIdentityVerifier.config().clientId();
		return new GoogleAuthConfigResponse(clientId);
	}

	public AuthResponse authenticateWithGoogle(GoogleAuthenticationRequest request) {
		return new AuthResponse(demoToken, 86_400, UiCommandFixtures.copyUser(UiCommandFixtures.demoUser()));
	}

	public User getMe(String authToken) {
		return delegate.getMe(demoToken);
	}

	public User updateMe(String authToken, UpdateUserRequest request) {
		return delegate.updateMe(demoToken, request);
	}

	public void deleteMe(String authToken) {
	}

	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		return delegate.purchaseCredits(demoToken, request);
	}

	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return delegate.listTwins(demoToken, status, type, q, sort, order);
	}

	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return delegate.createTwin(demoToken, request);
	}

	public DigitalTwin getTwin(String authToken, String twinId) {
		return delegate.getTwin(demoToken, twinId);
	}

	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		return delegate.updateTwin(demoToken, twinId, updates);
	}

	public void deleteTwin(String authToken, String twinId) {
		delegate.deleteTwin(demoToken, twinId);
	}

	public ModelContent getModel(String authToken, String twinId) {
		return delegate.getModel(demoToken, twinId);
	}

	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		return delegate.saveModel(demoToken, twinId, request);
	}

	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		return delegate.applyModelPrompt(demoToken, twinId, request);
	}

	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		return delegate.listSubjects(demoToken, twinId);
	}

	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		return delegate.getSubject(demoToken, twinId, subjectId);
	}

	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		return delegate.getSubjectTelemetry(demoToken, twinId, subjectId, historyPoints);
	}

	public IngestionToken getTwinIngestionToken(String authToken, String twinId) {
		return delegate.getTwinIngestionToken(demoToken, twinId);
	}

	public IngestionToken rotateTwinIngestionToken(String authToken, String twinId) {
		return delegate.rotateTwinIngestionToken(demoToken, twinId);
	}

	public void ingestSubjectSensorMetrics(String authToken, String twinId, String subjectId, IngestMetricsRequest request) {
		delegate.ingestSubjectSensorMetrics(authToken, twinId, subjectId, request);
	}

	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		return delegate.listDatasets(demoToken, twinId);
	}

	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		return delegate.getDataset(demoToken, twinId, subjectId);
	}

	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		return delegate.uploadDataset(demoToken, twinId, subjectId, fileName, content);
	}

	public void deleteDataset(String authToken, String twinId, String subjectId) {
		delegate.deleteDataset(demoToken, twinId, subjectId);
	}

	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		return delegate.getInferenceEngine(demoToken, twinId);
	}

	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		return delegate.saveEngineConfig(demoToken, twinId, request);
	}

	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		return delegate.saveRetrainingConfig(demoToken, twinId, request);
	}

	public TrainingJob launchTraining(String authToken, String twinId) {
		return delegate.launchTraining(demoToken, twinId);
	}

	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		return delegate.getTrainingJob(demoToken, twinId, jobId);
	}

	private static Application createDemoModel() {
		User demoUser = UiCommandFixtures.demoUser();
		UserAccount account = ModelViewMapper.toDomainUserAccount(demoUser, UiCommandFixtures.DEFAULT_GOOGLE_SUBJECT);
		List<TwinAggregate> demoTwins = UiCommandFixtures.demoTwins().stream()
				.map(ModelViewMapper::toDomainTwin)
				.map(twin -> new TwinAggregate(account.id(), twin))
				.toList();
		return new Application(List.of(account), List.of(), demoTwins, List.of());
	}

	private static GoogleIdentityVerifier defaultGoogleIdentityVerifier() {
		return new StubGoogleIdentityVerifier(
				new GoogleAuthConfig("demo-google-client-id"),
				new GoogleIdentity(UiCommandFixtures.DEFAULT_GOOGLE_SUBJECT, UiCommandFixtures.DEFAULT_EMAIL, true, UiCommandFixtures.demoUser().name())
		);
	}

	private static final class InMemoryModelPersistence implements ModelPersistence {
		private Application model;

		private InMemoryModelPersistence(Application initialModel) {
			this.model = initialModel == null ? Application.empty() : initialModel;
		}

		@Override
		public synchronized Optional<Application> loadModel() {
			return Optional.ofNullable(model);
		}

		@Override
		public synchronized void saveModel(Application model) {
			this.model = model == null ? Application.empty() : model;
		}

		@Override
		public void close() {
		}
	}
}
