package io.picota.backend.control.ui.schemas;

public record AuthResponse(
		String token,
		Integer expiresIn,
		User user
) {
}
