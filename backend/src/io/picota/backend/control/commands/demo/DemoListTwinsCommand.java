package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.ListTwinsCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.List;

public final class DemoListTwinsCommand implements ListTwinsCommand {
	private final DemoCommandState state;

	public DemoListTwinsCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return state.listTwins(authToken, status, type, q, sort, order);
	}
}
