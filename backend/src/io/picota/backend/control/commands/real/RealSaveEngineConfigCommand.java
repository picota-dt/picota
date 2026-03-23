package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.SaveEngineConfigCommand;
import io.picota.backend.control.ui.schemas.InferenceEngine;

public final class RealSaveEngineConfigCommand implements SaveEngineConfigCommand {
	private final RealCommandState state;

	public RealSaveEngineConfigCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public InferenceEngine saveEngineConfig(String authToken, String twinId, InferenceEngine request) {
		return state.saveEngineConfig(authToken, twinId, request);
	}
}
