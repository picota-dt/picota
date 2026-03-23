package io.picota.backend.control.commands;

import io.picota.backend.control.ui.*;

import java.util.List;
import java.util.Map;

public interface UiCommands {
	AuthResponse register(RegisterRequest request);

	AuthResponse login(LoginRequest request);

	void logout(String authToken);

	void changePassword(String authToken, ChangePasswordRequest request);

	User getMe(String authToken);

	User updateMe(String authToken, UpdateUserRequest request);

	void deleteMe(String authToken);

	PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request);

	List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order);

	DigitalTwin createTwin(String authToken, CreateTwinRequest request);

	DigitalTwin getTwin(String authToken, String twinId);

	DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates);

	void deleteTwin(String authToken, String twinId);

	ModelContent getModel(String authToken, String twinId);

	SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request);

	ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request);

	List<DigitalSubject> listSubjects(String authToken, String twinId);

	DigitalSubject getSubject(String authToken, String twinId, String subjectId);

	List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints);

	List<SubjectDataset> listDatasets(String authToken, String twinId);

	SubjectDataset getDataset(String authToken, String twinId, String subjectId);

	SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content);

	void deleteDataset(String authToken, String twinId, String subjectId);

	InferenceEngine getInferenceEngine(String authToken, String twinId);

	InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request);

	RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request);

	TrainingJob launchTraining(String authToken, String twinId);

	TrainingJob getTrainingJob(String authToken, String twinId, String jobId);
}
