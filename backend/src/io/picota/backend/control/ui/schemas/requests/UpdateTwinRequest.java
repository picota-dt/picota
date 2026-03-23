package io.picota.backend.control.ui.schemas.requests;

import io.picota.backend.control.ui.schemas.TwinStatus;

public record UpdateTwinRequest(
		String name,
		String description,
		TwinStatus status
) {
}
