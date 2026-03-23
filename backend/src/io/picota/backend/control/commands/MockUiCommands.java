package io.picota.backend.control.commands;

import io.picota.backend.control.ui.*;

import java.util.List;
import java.util.Map;

public class MockUiCommands implements UiCommands {
	private final RealUiCommands delegate;
	private final String mockToken;

	public MockUiCommands() {
		this.delegate = new RealUiCommands();
		this.mockToken = delegate.login(new LoginRequest(UiCommandFixtures.DEFAULT_EMAIL, UiCommandFixtures.DEFAULT_PASSWORD)).token();
	}

	@Override
	public AuthResponse register(RegisterRequest request) {
		return new AuthResponse(mockToken, 86_400, UiCommandFixtures.copyUser(UiCommandFixtures.demoUser()));
	}

	@Override
	public AuthResponse login(LoginRequest request) {
		return new AuthResponse(mockToken, 86_400, UiCommandFixtures.copyUser(UiCommandFixtures.demoUser()));
	}

	@Override
	public void logout(String authToken) {
	}

	@Override
	public void changePassword(String authToken, ChangePasswordRequest request) {
	}

	@Override
	public User getMe(String authToken) {
		return delegate.getMe(mockToken);
	}

	@Override
	public User updateMe(String authToken, UpdateUserRequest request) {
		return delegate.updateMe(mockToken, request);
	}

	@Override
	public void deleteMe(String authToken) {
	}

	@Override
	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		return delegate.purchaseCredits(mockToken, request);
	}

	@Override
	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return delegate.listTwins(mockToken, status, type, q, sort, order);
	}

	@Override
	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return delegate.createTwin(mockToken, request);
	}

	@Override
	public DigitalTwin getTwin(String authToken, String twinId) {
		return delegate.getTwin(mockToken, twinId);
	}

	@Override
	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		return delegate.updateTwin(mockToken, twinId, updates);
	}

	@Override
	public void deleteTwin(String authToken, String twinId) {
		delegate.deleteTwin(mockToken, twinId);
	}

	@Override
	public ModelContent getModel(String authToken, String twinId) {
		return delegate.getModel(mockToken, twinId);
	}

	@Override
	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		return delegate.saveModel(mockToken, twinId, request);
	}

	@Override
	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		return delegate.applyModelPrompt(mockToken, twinId, request);
	}

	@Override
	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		return delegate.listSubjects(mockToken, twinId);
	}

	@Override
	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		return delegate.getSubject(mockToken, twinId, subjectId);
	}

	@Override
	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		return delegate.getSubjectTelemetry(mockToken, twinId, subjectId, historyPoints);
	}

	@Override
	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		return delegate.listDatasets(mockToken, twinId);
	}

	@Override
	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		return delegate.getDataset(mockToken, twinId, subjectId);
	}

	@Override
	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		return delegate.uploadDataset(mockToken, twinId, subjectId, fileName, content);
	}

	@Override
	public void deleteDataset(String authToken, String twinId, String subjectId) {
		delegate.deleteDataset(mockToken, twinId, subjectId);
	}

	@Override
	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		return delegate.getInferenceEngine(mockToken, twinId);
	}

	@Override
	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		return delegate.saveEngineConfig(mockToken, twinId, request);
	}

	@Override
	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		return delegate.saveRetrainingConfig(mockToken, twinId, request);
	}

	@Override
	public TrainingJob launchTraining(String authToken, String twinId) {
		return delegate.launchTraining(mockToken, twinId);
	}

	@Override
	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		return delegate.getTrainingJob(mockToken, twinId, jobId);
	}
}
