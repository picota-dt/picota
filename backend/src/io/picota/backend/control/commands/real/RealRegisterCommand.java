package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.RegisterCommand;
import io.picota.backend.control.ui.schemas.AuthResponse;
import io.picota.backend.control.ui.schemas.requests.RegisterRequest;

public final class RealRegisterCommand implements RegisterCommand {
	private final RealCommandState state;

	public RealRegisterCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public AuthResponse register(RegisterRequest request) {
		return state.register(request);
	}
}
