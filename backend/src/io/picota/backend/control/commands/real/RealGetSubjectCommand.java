package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetSubjectCommand;
import io.picota.backend.control.ui.schemas.DigitalSubject;

public final class RealGetSubjectCommand implements GetSubjectCommand {
	private final RealCommandState state;

	public RealGetSubjectCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public DigitalSubject getSubject(String authToken, String twinId, String subjectId) {
		return state.getSubject(authToken, twinId, subjectId);
	}
}
