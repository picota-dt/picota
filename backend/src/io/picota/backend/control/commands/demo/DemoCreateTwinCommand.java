package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.CreateTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;
import io.picota.backend.control.ui.schemas.requests.CreateTwinRequest;

public final class DemoCreateTwinCommand implements CreateTwinCommand {
	private final DemoCommandState state;

	public DemoCreateTwinCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return state.createTwin(authToken, request);
	}
}
