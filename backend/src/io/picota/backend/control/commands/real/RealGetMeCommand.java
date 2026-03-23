package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetMeCommand;
import io.picota.backend.control.ui.schemas.User;

public final class RealGetMeCommand implements GetMeCommand {
	private final RealCommandState state;

	public RealGetMeCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public User getMe(String authToken) {
		return state.getMe(authToken);
	}
}
