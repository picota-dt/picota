package io.picota.backend.control.ui;

public record CreateTwinRequest(
		String name,
		TwinType type,
		String description
) {
}
