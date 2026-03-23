package io.picota.backend.control.ui;

import java.time.Instant;

public record SaveModelResponse(
		String version,
		Instant updatedAt
) {
}
