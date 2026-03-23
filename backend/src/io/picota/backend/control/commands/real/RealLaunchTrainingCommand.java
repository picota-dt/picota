package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.LaunchTrainingCommand;
import io.picota.backend.control.ui.schemas.TrainingJob;

public final class RealLaunchTrainingCommand implements LaunchTrainingCommand {
	private final RealCommandState state;

	public RealLaunchTrainingCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public TrainingJob launchTraining(String authToken, String twinId) {
		return state.launchTraining(authToken, twinId);
	}
}
