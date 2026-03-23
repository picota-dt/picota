package io.picota.backend.model;

import io.picota.backend.control.ui.User;

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
	public User toUiUser() {
		return new User(
				id,
				name,
				email,
				role,
				organization,
				avatarInitials,
				credits,
				joinedAt == null ? null : joinedAt.toString()
		);
	}

	public static UserAccount fromUiUser(User user, String passwordHash) {
		Instant joinedAt;
		try {
			joinedAt = user.joinedAt() == null || user.joinedAt().isBlank()
					? Instant.now()
					: Instant.parse(user.joinedAt());
		} catch (Exception ignored) {
			joinedAt = Instant.now();
		}
		return new UserAccount(
				user.id(),
				user.name(),
				user.email(),
				passwordHash,
				user.role(),
				user.organization(),
				user.avatarInitials(),
				user.credits() == null ? 0 : user.credits(),
				joinedAt
		);
	}
}
