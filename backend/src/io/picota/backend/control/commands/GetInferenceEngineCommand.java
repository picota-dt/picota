package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.InferenceEngine;

@FunctionalInterface
public interface GetInferenceEngineCommand {
	InferenceEngine getInferenceEngine(String authToken, String twinId);
}
