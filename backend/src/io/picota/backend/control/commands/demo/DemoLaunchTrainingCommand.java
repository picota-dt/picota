package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.LaunchTrainingCommand;
import io.picota.backend.control.ui.schemas.TrainingJob;

public final class DemoLaunchTrainingCommand implements LaunchTrainingCommand {
	private final DemoCommandState state;

	public DemoLaunchTrainingCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public TrainingJob launchTraining(String authToken, String twinId) {
		return state.launchTraining(authToken, twinId);
	}
}
