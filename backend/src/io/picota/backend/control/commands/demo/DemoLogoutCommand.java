package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.LogoutCommand;

public final class DemoLogoutCommand implements LogoutCommand {
	private final DemoCommandState state;

	public DemoLogoutCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void logout(String authToken) {
		state.logout(authToken);
	}
}
