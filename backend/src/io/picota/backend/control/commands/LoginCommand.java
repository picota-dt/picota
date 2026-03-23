package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.LoginRequest;

@FunctionalInterface
public interface LoginCommand {
	AuthResponse login(LoginRequest request);
}
