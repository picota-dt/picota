package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetMeCommand;
import io.picota.backend.control.ui.schemas.User;

public final class DemoGetMeCommand implements GetMeCommand {
	private final DemoCommandState state;

	public DemoGetMeCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public User getMe(String authToken) {
		return state.getMe(authToken);
	}
}
