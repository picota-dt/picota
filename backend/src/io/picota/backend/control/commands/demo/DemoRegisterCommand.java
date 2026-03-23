package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.RegisterCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.RegisterRequest;

public final class DemoRegisterCommand implements RegisterCommand {
	private final DemoCommandState state;

	public DemoRegisterCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse register(RegisterRequest request) {
		return state.register(request);
	}
}
