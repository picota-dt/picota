package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetInferenceEngineCommand;
import io.picota.backend.control.ui.schemas.InferenceEngine;

public final class RealGetInferenceEngineCommand implements GetInferenceEngineCommand {
	private final RealCommandState state;

	public RealGetInferenceEngineCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public InferenceEngine getInferenceEngine(String authToken, String twinId) {
		return state.getInferenceEngine(authToken, twinId);
	}
}
