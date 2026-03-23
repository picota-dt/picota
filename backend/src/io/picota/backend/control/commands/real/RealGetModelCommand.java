package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetModelCommand;
import io.picota.backend.control.ui.schemas.ModelContent;

public final class RealGetModelCommand implements GetModelCommand {
	private final RealCommandState state;

	public RealGetModelCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public ModelContent getModel(String authToken, String twinId) {
		return state.getModel(authToken, twinId);
	}
}
