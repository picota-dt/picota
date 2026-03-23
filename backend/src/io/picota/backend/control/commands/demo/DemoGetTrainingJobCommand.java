package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetTrainingJobCommand;
import io.picota.backend.control.ui.schemas.TrainingJob;

public final class DemoGetTrainingJobCommand implements GetTrainingJobCommand {
	private final DemoCommandState state;

	public DemoGetTrainingJobCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public TrainingJob getTrainingJob(String authToken, String twinId, String jobId) {
		return state.getTrainingJob(authToken, twinId, jobId);
	}
}
