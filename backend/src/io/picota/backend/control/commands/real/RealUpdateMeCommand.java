package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.UpdateMeCommand;
import io.picota.backend.control.ui.schemas.User;
import io.picota.backend.control.ui.schemas.requests.UpdateUserRequest;

public final class RealUpdateMeCommand implements UpdateMeCommand {
	private final RealCommandState state;

	public RealUpdateMeCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public User updateMe(String authToken, UpdateUserRequest request) {
		return state.updateMe(authToken, request);
	}
}
