package io.picota.backend.control.ui;

public record UpdateUserRequest(
		String name,
		String email,
		String organization
) {
}
