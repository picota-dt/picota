package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.AuthenticateWithGoogleCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.GoogleAuthenticationRequest;

public final class RealAuthenticateWithGoogleCommand implements AuthenticateWithGoogleCommand {
	private final RealCommandState state;

	public RealAuthenticateWithGoogleCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse authenticateWithGoogle(GoogleAuthenticationRequest request) {
		return state.authenticateWithGoogle(request);
	}
}
