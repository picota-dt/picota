package io.picota.backend.model;

import io.picota.backend.control.ui.DigitalTwin;

public record TwinAggregate(
		String ownerUserId,
		DigitalTwin twin
) {
}
