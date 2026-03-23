package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.ChangePasswordCommand;
import io.picota.backend.control.ui.schemas.requests.ChangePasswordRequest;

public final class DemoChangePasswordCommand implements ChangePasswordCommand {
	private final DemoCommandState state;

	public DemoChangePasswordCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void changePassword(String authToken, ChangePasswordRequest request) {
		state.changePassword(authToken, request);
	}
}
