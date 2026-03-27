package io.picota.backend.control.commands;

import io.picota.backend.control.auth.GoogleAuthConfig;
import io.picota.backend.control.auth.GoogleIdentityVerifier;
import io.picota.backend.control.auth.StubGoogleIdentityVerifier;
import io.picota.backend.control.commands.demo.*;
import io.picota.backend.control.commands.real.*;
import io.picota.backend.control.ingestion.DemoIngestSensorMetricsCommand;
import io.picota.backend.control.ingestion.RealIngestSensorMetricsCommand;
import io.picota.backend.control.training.ExternalTrainingClient;
import io.picota.backend.persistence.ModelPersistence;

import java.nio.file.Path;

public final class UiCommandsFactory {
	private UiCommandsFactory() {
	}

	public static UiCommandSet create(UiCommandsMode mode) {
		return create(mode, null, TwinModelTemplate.defaultTemplate(), defaultDatasetsRootDir(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence) {
		return create(mode, persistence, TwinModelTemplate.defaultTemplate(), defaultDatasetsRootDir(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate) {
		return create(mode, persistence, twinModelTemplate, defaultDatasetsRootDir(), ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir) {
		return create(mode, persistence, twinModelTemplate, datasetsRootDir, ExternalTrainingClient.disabled(), defaultGoogleIdentityVerifier());
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence, TwinModelTemplate twinModelTemplate, Path datasetsRootDir, ExternalTrainingClient trainingClient) {
		return create(mode, persistence, twinModelTemplate, datasetsRootDir, trainingClient, defaultGoogleIdentityVerifier());
	}

	public static UiCommandSet create(
			UiCommandsMode mode,
			ModelPersistence persistence,
			TwinModelTemplate twinModelTemplate,
			Path datasetsRootDir,
			ExternalTrainingClient trainingClient,
			GoogleIdentityVerifier googleIdentityVerifier
	) {
		TwinModelTemplate template = twinModelTemplate == null ? TwinModelTemplate.defaultTemplate() : twinModelTemplate;
		Path safeDatasetsRoot = datasetsRootDir == null ? defaultDatasetsRootDir() : datasetsRootDir.toAbsolutePath().normalize();
		ExternalTrainingClient safeTrainingClient = trainingClient == null ? ExternalTrainingClient.disabled() : trainingClient;
		GoogleIdentityVerifier safeGoogleIdentityVerifier = googleIdentityVerifier == null ? defaultGoogleIdentityVerifier() : googleIdentityVerifier;
		return mode == UiCommandsMode.DEMO
				? createDemoSet(template, safeGoogleIdentityVerifier)
				: createRealSet(persistence, template, safeDatasetsRoot, safeTrainingClient, safeGoogleIdentityVerifier);
	}

	private static UiCommandSet createRealSet(
			ModelPersistence persistence,
			TwinModelTemplate twinModelTemplate,
			Path datasetsRootDir,
			ExternalTrainingClient trainingClient,
			GoogleIdentityVerifier googleIdentityVerifier
	) {
		RealCommandState state = new RealCommandState(persistence, twinModelTemplate, new io.picota.backend.persistence.FilesystemDatasetStorage(datasetsRootDir), trainingClient, googleIdentityVerifier);
		return new UiCommandSet(
				new RealGetGoogleAuthConfigCommand(state),
				new RealAuthenticateWithGoogleCommand(state),
				new RealLogoutCommand(state),
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
				new RealGetTwinIngestionTokenCommand(state),
				new RealRotateTwinIngestionTokenCommand(state),
				new RealIngestSensorMetricsCommand(state),
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

	private static UiCommandSet createDemoSet(TwinModelTemplate twinModelTemplate, GoogleIdentityVerifier googleIdentityVerifier) {
		DemoCommandState state = new DemoCommandState(twinModelTemplate, googleIdentityVerifier);
		return new UiCommandSet(
				new DemoGetGoogleAuthConfigCommand(state),
				new DemoAuthenticateWithGoogleCommand(state),
				new DemoLogoutCommand(state),
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
				new DemoGetTwinIngestionTokenCommand(state),
				new DemoRotateTwinIngestionTokenCommand(state),
				new DemoIngestSensorMetricsCommand(state),
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

	private static GoogleIdentityVerifier defaultGoogleIdentityVerifier() {
		return new StubGoogleIdentityVerifier(new GoogleAuthConfig("demo-google-client-id"), null);
	}
}
