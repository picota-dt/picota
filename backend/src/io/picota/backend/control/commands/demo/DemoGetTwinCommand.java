package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

public final class DemoGetTwinCommand implements GetTwinCommand {
	private final DemoCommandState state;

	public DemoGetTwinCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin getTwin(String authToken, String twinId) {
		return state.getTwin(authToken, twinId);
	}
}
