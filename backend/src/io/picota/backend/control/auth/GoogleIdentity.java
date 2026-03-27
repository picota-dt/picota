package io.picota.backend.control.auth;

public record GoogleIdentity(
		String subject,
		String email,
		boolean emailVerified,
		String name
) {
}
