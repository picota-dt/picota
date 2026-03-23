package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.LogoutCommand;

public final class RealLogoutCommand implements LogoutCommand {
	private final RealCommandState state;

	public RealLogoutCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void logout(String authToken) {
		state.logout(authToken);
	}
}
