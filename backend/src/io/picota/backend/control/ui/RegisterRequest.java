package io.picota.backend.control.ui;

public record RegisterRequest(
		String name,
		String email,
		String password,
		String organization
) {
}
