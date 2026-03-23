package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.ListSubjectsCommand;
import io.picota.backend.control.ui.schemas.DigitalSubject;

import java.util.List;

public final class RealListSubjectsCommand implements ListSubjectsCommand {
	private final RealCommandState state;

	public RealListSubjectsCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		return state.listSubjects(authToken, twinId);
	}
}
