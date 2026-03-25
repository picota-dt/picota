package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.IngestionToken;

@FunctionalInterface
public interface RotateTwinIngestionTokenCommand {
	IngestionToken rotateTwinIngestionToken(String authToken, String twinId);
}
