package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.AuthenticateWithGoogleCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.GoogleAuthenticationRequest;

public final class DemoAuthenticateWithGoogleCommand implements AuthenticateWithGoogleCommand {
	private final DemoCommandState state;

	public DemoAuthenticateWithGoogleCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse authenticateWithGoogle(GoogleAuthenticationRequest request) {
		return state.authenticateWithGoogle(request);
	}
}
