package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.GoogleAuthenticationRequest;

@FunctionalInterface
public interface AuthenticateWithGoogleCommand {
	AuthResponse authenticateWithGoogle(GoogleAuthenticationRequest request);
}
