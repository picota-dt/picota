package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.UpdateTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.Map;

public final class RealUpdateTwinCommand implements UpdateTwinCommand {
	private final RealCommandState state;

	public RealUpdateTwinCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		return state.updateTwin(authToken, twinId, updates);
	}
}
