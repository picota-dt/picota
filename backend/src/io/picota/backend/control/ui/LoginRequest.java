package io.picota.backend.control.ui;

public record LoginRequest(
		String email,
		String password
) {
}
