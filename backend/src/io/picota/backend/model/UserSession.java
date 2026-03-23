package io.picota.backend.model;

import java.time.Instant;

public record UserSession(
		String token,
		String userId,
		Instant issuedAt
) {
}
