package io.picota.backend.model;

import io.picota.backend.control.ui.TrainingJob;

public record TrainingJobAggregate(
		String ownerUserId,
		TrainingJob job
) {
}
