package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetModelCommand;
import io.picota.backend.control.ui.schemas.ModelContent;

public final class DemoGetModelCommand implements GetModelCommand {
	private final DemoCommandState state;

	public DemoGetModelCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public ModelContent getModel(String authToken, String twinId) {
		return state.getModel(authToken, twinId);
	}
}
