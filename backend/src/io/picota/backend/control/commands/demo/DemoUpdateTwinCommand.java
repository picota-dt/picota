package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.UpdateTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.Map;

public final class DemoUpdateTwinCommand implements UpdateTwinCommand {
	private final DemoCommandState state;

	public DemoUpdateTwinCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates) {
		return state.updateTwin(authToken, twinId, updates);
	}
}
