package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.DeleteMeCommand;

public final class DemoDeleteMeCommand implements DeleteMeCommand {
	private final DemoCommandState state;

	public DemoDeleteMeCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteMe(String authToken) {
		state.deleteMe(authToken);
	}
}
