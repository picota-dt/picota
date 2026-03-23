package io.picota.backend.control.ui.schemas.requests;

public record ChangePasswordRequest(
		String currentPassword,
		String newPassword
) {
}
