package io.picota.backend.control.ui;

import java.time.Instant;

public record TelemetryPoint(
		Instant time,
		Double value
) {
}
