package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.LoginCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.LoginRequest;

public final class DemoLoginCommand implements LoginCommand {
	private final DemoCommandState state;

	public DemoLoginCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse login(LoginRequest request) {
		return state.login(request);
	}
}
