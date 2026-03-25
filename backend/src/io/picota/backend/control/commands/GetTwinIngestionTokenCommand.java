package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.IngestionToken;

@FunctionalInterface
public interface GetTwinIngestionTokenCommand {
	IngestionToken getTwinIngestionToken(String authToken, String twinId);
}
