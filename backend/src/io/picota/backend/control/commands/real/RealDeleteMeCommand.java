package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.DeleteMeCommand;

public final class RealDeleteMeCommand implements DeleteMeCommand {
	private final RealCommandState state;

	public RealDeleteMeCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteMe(String authToken) {
		state.deleteMe(authToken);
	}
}
