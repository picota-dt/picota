package io.picota.backend.control.commands;

import io.picota.backend.control.commands.demo.*;
import io.picota.backend.control.commands.real.*;
import io.picota.backend.control.training.ExternalTrainingClient;
import io.picota.backend.persistence.ModelPersistence;

import java.nio.file.Path;

public final class UiCommandsFactory {
	private UiCommandsFactory() {
	}

	public static UiCommandSet create(UiCommandsMode mode) {
		return create(mode, null, TwinModelTemplate.defaultTemplate(), defaultDatasetsRootDir(), ExternalTrainingClient.disabled());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence) {
		return create(mode, persistence, TwinModelTemplate.defaultTemplate(), defaultDatasetsRootDir(), ExternalTrainingClient.disabled());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate) {
		return create(mode, persistence, twinModelTemplate, defaultDatasetsRootDir(), ExternalTrainingClient.disabled());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir) {
		return create(mode, persistence, twinModelTemplate, datasetsRootDir, ExternalTrainingClient.disabled());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir, ExternalTrainingClient trainingClient) {
		TwinModelTemplate template = twinModelTemplate == null ? TwinModelTemplate.defaultTemplate() : twinModelTemplate;
		Path safeDatasetsRoot = datasetsRootDir == null ? defaultDatasetsRootDir() : datasetsRootDir.toAbsolutePath().normalize();
		ExternalTrainingClient safeTrainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
		return mode == UiCommandsMode.DEMO ? createDemoSet(template) : createRealSet(persistence, template, safeDatasetsRoot, safeTrainingClient);
	}

	private static UiCommandSet createRealSet(ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir, ExternalTrainingClient trainingClient) {
		RealCommandState state = new RealCommandState(persistence, twinModelTemplate, datasetsRootDir, trainingClient);
		return new UiCommandSet(
				new RealRegisterCommand(state),
				new RealLoginCommand(state),
				new RealLogoutCommand(state),
				new RealChangePasswordCommand(state),
				new RealGetMeCommand(state),
				new RealUpdateMeCommand(state),
				new RealDeleteMeCommand(state),
				new RealPurchaseCreditsCommand(state),
				new RealListTwinsCommand(state),
				new RealCreateTwinCommand(state),
				new RealGetTwinCommand(state),
				new RealUpdateTwinCommand(state),
				new RealDeleteTwinCommand(state),
				new RealGetModelCommand(state),
				new RealSaveModelCommand(state),
				new RealApplyModelPromptCommand(state),
				new RealListSubjectsCommand(state),
				new RealGetSubjectCommand(state),
				new RealGetSubjectTelemetryCommand(state),
				new RealListDatasetsCommand(state),
				new RealGetDatasetCommand(state),
				new RealUploadDatasetCommand(state),
				new RealDeleteDatasetCommand(state),
				new RealGetInferenceEngineCommand(state),
				new RealSaveEngineConfigCommand(state),
				new RealSaveRetrainingConfigCommand(state),
				new RealLaunchTrainingCommand(state),
				new RealGetTrainingJobCommand(state)
		);
	}

	private static UiCommandSet createDemoSet(TwinModelTemplate twinModelTemplate) {
		DemoCommandState state = new DemoCommandState(twinModelTemplate);
		return new UiCommandSet(
				new DemoRegisterCommand(state),
				new DemoLoginCommand(state),
				new DemoLogoutCommand(state),
				new DemoChangePasswordCommand(state),
				new DemoGetMeCommand(state),
				new DemoUpdateMeCommand(state),
				new DemoDeleteMeCommand(state),
				new DemoPurchaseCreditsCommand(state),
				new DemoListTwinsCommand(state),
				new DemoCreateTwinCommand(state),
				new DemoGetTwinCommand(state),
				new DemoUpdateTwinCommand(state),
				new DemoDeleteTwinCommand(state),
				new DemoGetModelCommand(state),
				new DemoSaveModelCommand(state),
				new DemoApplyModelPromptCommand(state),
				new DemoListSubjectsCommand(state),
				new DemoGetSubjectCommand(state),
				new DemoGetSubjectTelemetryCommand(state),
				new DemoListDatasetsCommand(state),
				new DemoGetDatasetCommand(state),
				new DemoUploadDatasetCommand(state),
				new DemoDeleteDatasetCommand(state),
				new DemoGetInferenceEngineCommand(state),
				new DemoSaveEngineConfigCommand(state),
				new DemoSaveRetrainingConfigCommand(state),
				new DemoLaunchTrainingCommand(state),
				new DemoGetTrainingJobCommand(state)
		);
	}

	private static Path defaultDatasetsRootDir() {
		return Path.of("./runtime/datasets").toAbsolutePath().normalize();
	}
}
