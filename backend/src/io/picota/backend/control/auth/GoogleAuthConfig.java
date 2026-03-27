package io.picota.backend.control.auth;

public record GoogleAuthConfig(
		String clientId
) {
	public GoogleAuthConfig {
		clientId = clientId == null ? "" : clientId.trim();
	}

	public boolean configured() {
		return !clientId.isBlank();
	}
}
