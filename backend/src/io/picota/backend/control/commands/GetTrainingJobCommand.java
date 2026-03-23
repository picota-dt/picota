package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.TrainingJob;

@FunctionalInterface
public interface GetTrainingJobCommand {
	TrainingJob getTrainingJob(String authToken, String twinId, String jobId);
}
