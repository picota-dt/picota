package io.picota.backend.control.ui.schemas;

import java.time.Instant;

public record TelemetryPoint(
		Instant time,
		Double value
) {
}
