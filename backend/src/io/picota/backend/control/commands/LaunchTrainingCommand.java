package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.TrainingJob;

@FunctionalInterface
public interface LaunchTrainingCommand {
	TrainingJob launchTraining(String authToken, String twinId);
}
