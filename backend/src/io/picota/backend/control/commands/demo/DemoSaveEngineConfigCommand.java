package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.SaveEngineConfigCommand;
import io.picota.backend.control.ui.schemas.InferenceEngine;

public final class DemoSaveEngineConfigCommand implements SaveEngineConfigCommand {
	private final DemoCommandState state;

	public DemoSaveEngineConfigCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		return state.saveEngineConfig(authToken, twinId, request);
	}
}
