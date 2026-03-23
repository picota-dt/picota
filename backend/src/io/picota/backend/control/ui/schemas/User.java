package io.picota.backend.control.ui.schemas;

public record User(
		String id,
		String name,
		String email,
		String avatarInitials,
		Integer credits,
		String joinedAt
) {
}
