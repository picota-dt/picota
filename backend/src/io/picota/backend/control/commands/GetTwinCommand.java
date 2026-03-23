package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalTwin;

@FunctionalInterface
public interface GetTwinCommand {
	DigitalTwin getTwin(String authToken, String twinId);
}
