package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.RegisterRequest;

@FunctionalInterface
public interface RegisterCommand {
	AuthResponse register(RegisterRequest request);
}
