package io.picota.backend.control.ui.schemas.requests;

public record RegisterRequest(
		String name,
		String email,
		String password,
		String organization
) {
}
