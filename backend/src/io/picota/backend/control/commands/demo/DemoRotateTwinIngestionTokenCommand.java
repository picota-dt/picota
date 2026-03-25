package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.RotateTwinIngestionTokenCommand;
import io.picota.backend.control.ui.schemas.IngestionToken;

public final class DemoRotateTwinIngestionTokenCommand implements RotateTwinIngestionTokenCommand {
	private final DemoCommandState state;

	public DemoRotateTwinIngestionTokenCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public IngestionToken rotateTwinIngestionToken(String authToken, String twinId) {
		return state.rotateTwinIngestionToken(authToken, twinId);
	}
}
