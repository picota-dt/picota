package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.DeleteTwinCommand;

public final class RealDeleteTwinCommand implements DeleteTwinCommand {
	private final RealCommandState state;

	public RealDeleteTwinCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteTwin(String authToken, String twinId) {
		state.deleteTwin(authToken, twinId);
	}
}
