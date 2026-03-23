package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.Map;

@FunctionalInterface
public interface UpdateTwinCommand {
	DigitalTwin updateTwin(String authToken, String twinId, Map<String, Object> updates);
}
