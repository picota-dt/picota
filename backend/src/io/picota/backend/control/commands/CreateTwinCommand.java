package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalTwin;
import io.picota.backend.control.ui.schemas.requests.CreateTwinRequest;

@FunctionalInterface
public interface CreateTwinCommand {
	DigitalTwin createTwin(String authToken, CreateTwinRequest request);
}
