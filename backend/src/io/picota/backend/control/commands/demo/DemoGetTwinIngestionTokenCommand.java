package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetTwinIngestionTokenCommand;
import io.picota.backend.control.ui.schemas.IngestionToken;

public final class DemoGetTwinIngestionTokenCommand implements GetTwinIngestionTokenCommand {
	private final DemoCommandState state;

	public DemoGetTwinIngestionTokenCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public IngestionToken getTwinIngestionToken(String authToken, String twinId) {
		return state.getTwinIngestionToken(authToken, twinId);
	}
}
