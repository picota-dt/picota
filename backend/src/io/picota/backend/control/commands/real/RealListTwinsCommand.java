package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.ListTwinsCommand;
import io.picota.backend.control.ui.schemas.DigitalTwin;

import java.util.List;

public final class RealListTwinsCommand implements ListTwinsCommand {
	private final RealCommandState state;

	public RealListTwinsCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public List<DigitalTwin> listTwins(String authToken, String status, String type, String q, String sort, String order) {
		return state.listTwins(authToken, status, type, q, sort, order);
	}
}
