package io.picota.backend.model;

public record TrainingJobAggregate(
		String ownerUserId,
		TrainingJob job
) {
}
