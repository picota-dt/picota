package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.requests.ChangePasswordRequest;

@FunctionalInterface
public interface ChangePasswordCommand {
	void changePassword(String authToken, ChangePasswordRequest request);
}
