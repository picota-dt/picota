package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.*;
import io.picota.backend.control.ui.schemas.requests.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record UiCommandSet(
		RegisterCommand registerCommand,
		LoginCommand loginCommand,
		LogoutCommand logoutCommand,
		ChangePasswordCommand changePasswordCommand,
		GetMeCommand getMeCommand,
		UpdateMeCommand updateMeCommand,
		DeleteMeCommand deleteMeCommand,
		PurchaseCreditsCommand purchaseCreditsCommand,
		ListTwinsCommand listTwinsCommand,
		CreateTwinCommand createTwinCommand,
		GetTwinCommand getTwinCommand,
		UpdateTwinCommand updateTwinCommand,
		DeleteTwinCommand deleteTwinCommand,
		GetModelCommand getModelCommand,
		SaveModelCommand saveModelCommand,
		ApplyModelPromptCommand applyModelPromptCommand,
		ListSubjectsCommand listSubjectsCommand,
		GetSubjectCommand getSubjectCommand,
		GetSubjectTelemetryCommand getSubjectTelemetryCommand,
		ListDatasetsCommand listDatasetsCommand,
		GetDatasetCommand getDatasetCommand,
		UploadDatasetCommand uploadDatasetCommand,
		DeleteDatasetCommand deleteDatasetCommand,
		GetInferenceEngineCommand getInferenceEngineCommand,
		SaveEngineConfigCommand saveEngineConfigCommand,
		SaveRetrainingConfigCommand saveRetrainingConfigCommand,
		LaunchTrainingCommand launchTrainingCommand,
		GetTrainingJobCommand getTrainingJobCommand
) {
	public UiCommandSet {
		registerCommand = Objects.requireNonNull(registerCommand, "registerCommand");
		loginCommand = Objects.requireNonNull(loginCommand, "loginCommand");
		logoutCommand = Objects.requireNonNull(logoutCommand, "logoutCommand");
		changePasswordCommand = Objects.requireNonNull(changePasswordCommand, "changePasswordCommand");
		getMeCommand = Objects.requireNonNull(getMeCommand, "getMeCommand");
		updateMeCommand = Objects.requireNonNull(updateMeCommand, "updateMeCommand");
		deleteMeCommand = Objects.requireNonNull(deleteMeCommand, "deleteMeCommand");
		purchaseCreditsCommand = Objects.requireNonNull(purchaseCreditsCommand, "purchaseCreditsCommand");
		listTwinsCommand = Objects.requireNonNull(listTwinsCommand, "listTwinsCommand");
		createTwinCommand = Objects.requireNonNull(createTwinCommand, "createTwinCommand");
		getTwinCommand = Objects.requireNonNull(getTwinCommand, "getTwinCommand");
		updateTwinCommand = Objects.requireNonNull(updateTwinCommand, "updateTwinCommand");
		deleteTwinCommand = Objects.requireNonNull(deleteTwinCommand, "deleteTwinCommand");
		getModelCommand = Objects.requireNonNull(getModelCommand, "getModelCommand");
		saveModelCommand = Objects.requireNonNull(saveModelCommand, "saveModelCommand");
		applyModelPromptCommand = Objects.requireNonNull(applyModelPromptCommand, "applyModelPromptCommand");
		listSubjectsCommand = Objects.requireNonNull(listSubjectsCommand, "listSubjectsCommand");
		getSubjectCommand = Objects.requireNonNull(getSubjectCommand, "getSubjectCommand");
		getSubjectTelemetryCommand = Objects.requireNonNull(getSubjectTelemetryCommand, "getSubjectTelemetryCommand");
		listDatasetsCommand = Objects.requireNonNull(listDatasetsCommand, "listDatasetsCommand");
		getDatasetCommand = Objects.requireNonNull(getDatasetCommand, "getDatasetCommand");
		uploadDatasetCommand = Objects.requireNonNull(uploadDatasetCommand, "uploadDatasetCommand");
		deleteDatasetCommand = Objects.requireNonNull(deleteDatasetCommand, "deleteDatasetCommand");
		getInferenceEngineCommand = Objects.requireNonNull(getInferenceEngineCommand, "getInferenceEngineCommand");
		saveEngineConfigCommand = Objects.requireNonNull(saveEngineConfigCommand, "saveEngineConfigCommand");
		saveRetrainingConfigCommand = Objects.requireNonNull(saveRetrainingConfigCommand, "saveRetrainingConfigCommand");
		launchTrainingCommand = Objects.requireNonNull(launchTrainingCommand, "launchTrainingCommand");
		getTrainingJobCommand = Objects.requireNonNull(getTrainingJobCommand, "getTrainingJobCommand");
	}

	public AuthResponse register(RegisterRequest request) {
		return registerCommand.register(request);
	}

	public AuthResponse login(LoginRequest request) {
		return loginCommand.login(request);
	}

	public void logout(String authToken) {
		logoutCommand.logout(authToken);
	}

	public void changePassword(String authToken, ChangePasswordRequest request) {
		changePasswordCommand.changePassword(authToken, request);
	}

	public User getMe(String authToken) {
		return getMeCommand.getMe(authToken);
	}

	public User updateMe(String authToken, UpdateUserRequest request) {
		return updateMeCommand.updateMe(authToken, request);
	}

	public void deleteMe(String authToken) {
		deleteMeCommand.deleteMe(authToken);
	}

	public PurchaseCreditsResponse purchaseCredits(String authToken, PurchaseCreditsRequest request) {
		return purchaseCreditsCommand.purchaseCredits(authToken, request);
	}

	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return listTwinsCommand.listTwins(authToken, status, type, q, sort, order);
	}

	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return createTwinCommand.createTwin(authToken, request);
	}

	public DigitalTwin getTwin(String authToken, String twinId) {
		return getTwinCommand.getTwin(authToken, twinId);
	}

	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		return updateTwinCommand.updateTwin(authToken, twinId, updates);
	}

	public void deleteTwin(String authToken, String twinId) {
		deleteTwinCommand.deleteTwin(authToken, twinId);
	}

	public ModelContent getModel(String authToken, String twinId) {
		return getModelCommand.getModel(authToken, twinId);
	}

	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		return saveModelCommand.saveModel(authToken, twinId, request);
	}

	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		return applyModelPromptCommand.applyModelPrompt(authToken, twinId, request);
	}

	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		return listSubjectsCommand.listSubjects(authToken, twinId);
	}

	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		return getSubjectCommand.getSubject(authToken, twinId, subjectId);
	}

	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		return getSubjectTelemetryCommand.getSubjectTelemetry(authToken, twinId, subjectId, historyPoints);
	}

	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		return listDatasetsCommand.listDatasets(authToken, twinId);
	}

	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		return getDatasetCommand.getDataset(authToken, twinId, subjectId);
	}

	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		return uploadDatasetCommand.uploadDataset(authToken, twinId, subjectId, fileName, content);
	}

	public void deleteDataset(String authToken, String twinId, String subjectId) {
		deleteDatasetCommand.deleteDataset(authToken, twinId, subjectId);
	}

	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		return getInferenceEngineCommand.getInferenceEngine(authToken, twinId);
	}

	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		return saveEngineConfigCommand.saveEngineConfig(authToken, twinId, request);
	}

	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		return saveRetrainingConfigCommand.saveRetrainingConfig(authToken, twinId, request);
	}

	public TrainingJob launchTraining(String authToken, String twinId) {
		return launchTrainingCommand.launchTraining(authToken, twinId);
	}

	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		return getTrainingJobCommand.getTrainingJob(authToken, twinId, jobId);
	}
}
