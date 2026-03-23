package io.picota.backend.model;

import java.time.Instant;

public record UserAccount(
		String id,
		String name,
		String email,
		String passwordHash,
		String role,
		String organization,
		String avatarInitials,
		int credits,
		Instant joinedAt
) {
}
