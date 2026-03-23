package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.SaveModelCommand;
import io.picota.backend.control.ui.schemas.SaveModelResponse;
import io.picota.backend.control.ui.schemas.requests.SaveModelRequest;

public final class DemoSaveModelCommand implements SaveModelCommand {
	private final DemoCommandState state;

	public DemoSaveModelCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request) {
		return state.saveModel(authToken, twinId, request);
	}
}
