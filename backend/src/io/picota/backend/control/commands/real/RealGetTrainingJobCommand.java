package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetTrainingJobCommand;
import io.picota.backend.control.ui.schemas.TrainingJob;

public final class RealGetTrainingJobCommand implements GetTrainingJobCommand {
	private final RealCommandState state;

	public RealGetTrainingJobCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		return state.getTrainingJob(authToken, twinId, jobId);
	}
}
