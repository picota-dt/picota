package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.UpdateMeCommand;
import io.picota.backend.control.ui.schemas.User;
import io.picota.backend.control.ui.schemas.requests.UpdateUserRequest;

public final class DemoUpdateMeCommand implements UpdateMeCommand {
	private final DemoCommandState state;

	public DemoUpdateMeCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public User updateMe(String authToken, UpdateUserRequest request) {
		return state.updateMe(authToken, request);
	}
}
