package io.picota.backend.control.ui.schemas.requests;

import io.picota.backend.control.ui.schemas.TwinType;

public record CreateTwinRequest(
		String name,
		TwinType type,
		String description
) {
}
