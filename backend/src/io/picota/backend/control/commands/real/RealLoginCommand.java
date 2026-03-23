package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.LoginCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.LoginRequest;

public final class RealLoginCommand implements LoginCommand {
	private final RealCommandState state;

	public RealLoginCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse login(LoginRequest request) {
		return state.login(request);
	}
}
