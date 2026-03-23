package io.picota.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserAccount(
		String id,
		String name,
		String email,
		String passwordHash,
		String avatarInitials,
		int credits,
		Instant joinedAt
) {
}
