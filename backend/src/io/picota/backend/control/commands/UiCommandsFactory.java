package io.picota.backend.control.commands;

import io.picota.backend.control.commands.demo.*;
import io.picota.backend.control.commands.real.*;
import io.picota.backend.persistence.ModelPersistence;

public final class UiCommandsFactory {
	private UiCommandsFactory() {
	}

	public static UiCommandSet create(UiCommandsMode mode) {
		return create(mode, null);
	}

	public static UiCommandSet create(UiCommandsMode mode, ModelPersistence persistence) {
		return mode == UiCommandsMode.DEMO ? createDemoSet() : createRealSet(persistence);
	}

	private static UiCommandSet createRealSet(ModelPersistence persistence) {
		RealCommandState state = new RealCommandState(persistence);
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

	private static UiCommandSet createDemoSet() {
		DemoCommandState state = new DemoCommandState();
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
}
