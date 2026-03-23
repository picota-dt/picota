package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

public final class RealGetTwinCommand implements GetTwinCommand {
	private final RealCommandState state;

	public RealGetTwinCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin getTwin(String authToken, String twinId) {
		return state.getTwin(authToken, twinId);
	}
}
