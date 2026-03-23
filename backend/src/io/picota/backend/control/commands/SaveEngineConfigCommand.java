package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.InferenceEngine;

@FunctionalInterface
public interface SaveEngineConfigCommand {
	InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request);
}
