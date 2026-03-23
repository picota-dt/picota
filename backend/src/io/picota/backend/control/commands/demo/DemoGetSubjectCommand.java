package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetSubjectCommand;
import io.picota.backend.control.ui.schemas.DigitalSubject;

public final class DemoGetSubjectCommand implements GetSubjectCommand {
	private final DemoCommandState state;

	public DemoGetSubjectCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		return state.getSubject(authToken, twinId, subjectId);
	}
}
