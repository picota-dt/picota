package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetInferenceEngineCommand;
import io.picota.backend.control.ui.schemas.InferenceEngine;

public final class DemoGetInferenceEngineCommand implements GetInferenceEngineCommand {
	private final DemoCommandState state;

	public DemoGetInferenceEngineCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		return state.getInferenceEngine(authToken, twinId);
	}
}
