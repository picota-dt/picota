package io.picota.backend.control.ui.schemas;

import java.time.Instant;

public record SaveModelResponse(
		String version,
		Instant updatedAt
) {
}
