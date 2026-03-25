package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.RotateTwinIngestionTokenCommand;
import io.picota.backend.control.ui.schemas.IngestionToken;

public final class RealRotateTwinIngestionTokenCommand implements RotateTwinIngestionTokenCommand {
	private final RealCommandState state;

	public RealRotateTwinIngestionTokenCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public IngestionToken rotateTwinIngestionToken(String authToken, String twinId) {
		return state.rotateTwinIngestionToken(authToken, twinId);
	}
}
