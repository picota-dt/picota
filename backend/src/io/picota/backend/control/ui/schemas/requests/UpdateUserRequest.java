package io.picota.backend.control.ui.schemas.requests;

public record UpdateUserRequest(
		String name,
		String email
) {
}
