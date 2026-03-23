package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.SaveModelCommand;
import io.picota.backend.control.ui.schemas.SaveModelResponse;
import io.picota.backend.control.ui.schemas.requests.SaveModelRequest;

public final class RealSaveModelCommand implements SaveModelCommand {
	private final RealCommandState state;

	public RealSaveModelCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		return state.saveModel(authToken, twinId, request);
	}
}
