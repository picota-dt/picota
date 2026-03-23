package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.ListSubjectsCommand;
import io.picota.backend.control.ui.schemas.DigitalSubject;

import java.util.List;

public final class DemoListSubjectsCommand implements ListSubjectsCommand {
	private final DemoCommandState state;

	public DemoListSubjectsCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public List<DigitalSubject> listSubjects(String authToken, String twinId) {
		return state.listSubjects(authToken, twinId);
	}
}
