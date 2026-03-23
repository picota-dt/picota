package io.picota.backend.model;

public record TwinAggregate(
		String ownerUserId,
		DigitalTwin twin
) {
}
