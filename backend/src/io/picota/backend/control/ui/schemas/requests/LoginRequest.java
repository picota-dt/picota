package io.picota.backend.control.ui.schemas.requests;

public record LoginRequest(
		String email,
		String password
) {
}
