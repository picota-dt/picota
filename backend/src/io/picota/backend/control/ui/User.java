package io.picota.backend.control.ui;

public record User(
		String id,
		String name,
		String email,
		String role,
		String organization,
		String avatarInitials,
		Integer credits,
		String joinedAt
) {
}
