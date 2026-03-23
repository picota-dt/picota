package io.picota.backend.control.ui;

public record ChangePasswordRequest(
		String currentPassword,
		String newPassword
) {
}
