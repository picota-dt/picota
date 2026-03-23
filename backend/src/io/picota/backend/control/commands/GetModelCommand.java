package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.ModelContent;

@FunctionalInterface
public interface GetModelCommand {
	ModelContent getModel(String authToken, String twinId);
}
