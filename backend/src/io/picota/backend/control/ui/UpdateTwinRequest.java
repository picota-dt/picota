package io.picota.backend.control.ui;

public record UpdateTwinRequest(
		String name,
		String description,
		TwinStatus status
) {
}
