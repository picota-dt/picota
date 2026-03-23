package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.DeleteTwinCommand;

public final class DemoDeleteTwinCommand implements DeleteTwinCommand {
	private final DemoCommandState state;

	public DemoDeleteTwinCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteTwin(String authToken, String twinId) {
		state.deleteTwin(authToken, twinId);
	}
}
