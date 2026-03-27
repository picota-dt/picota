package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetGoogleAuthConfigCommand;
import io.picota.backend.control.ui.schemas.GoogleAuthConfigResponse;

public final class RealGetGoogleAuthConfigCommand implements GetGoogleAuthConfigCommand {
	private final RealCommandState state;

	public RealGetGoogleAuthConfigCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public GoogleAuthConfigResponse getGoogleAuthConfig() {
		return state.getGoogleAuthConfig();
	}
}
