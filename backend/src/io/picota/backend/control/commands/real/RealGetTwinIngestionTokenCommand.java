package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetTwinIngestionTokenCommand;
import io.picota.backend.control.ui.schemas.IngestionToken;

public final class RealGetTwinIngestionTokenCommand implements GetTwinIngestionTokenCommand {
	private final RealCommandState state;

	public RealGetTwinIngestionTokenCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public IngestionToken getTwinIngestionToken(String authToken, String twinId) {
		return state.getTwinIngestionToken(authToken, twinId);
	}
}
