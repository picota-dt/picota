package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetGoogleAuthConfigCommand;
import io.picota.backend.control.ui.schemas.GoogleAuthConfigResponse;

public final class DemoGetGoogleAuthConfigCommand implements GetGoogleAuthConfigCommand {
	private final DemoCommandState state;

	public DemoGetGoogleAuthConfigCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public GoogleAuthConfigResponse getGoogleAuthConfig() {
		return state.getGoogleAuthConfig();
	}
}
