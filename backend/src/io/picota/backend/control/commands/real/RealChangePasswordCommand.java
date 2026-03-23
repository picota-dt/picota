package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.ChangePasswordCommand;
import io.picota.backend.control.ui.schemas.requests.ChangePasswordRequest;

public final class RealChangePasswordCommand implements ChangePasswordCommand {
	private final RealCommandState state;

	public RealChangePasswordCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void changePassword(String authToken, ChangePasswordRequest request) {
		state.changePassword(authToken, request);
	}
}
