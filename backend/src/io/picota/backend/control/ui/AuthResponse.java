package io.picota.backend.control.ui;

public record AuthResponse(
		String token,
		Integer expiresIn,
		User user
) {
}
