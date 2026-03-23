package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.CreateTwinCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;
import io.picota.backend.control.ui.schemas.requests.CreateTwinRequest;

public final class RealCreateTwinCommand implements CreateTwinCommand {
	private final RealCommandState state;

	public RealCreateTwinCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalTwin createTwin(String authToken, CreateTwinRequest request) {
		return state.createTwin(authToken, request);
	}
}
