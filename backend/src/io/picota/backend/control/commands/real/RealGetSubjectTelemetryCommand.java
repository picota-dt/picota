package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetSubjectTelemetryCommand;
import io.picota.backend.control.ui.schemas.VariableTelemetry;

import java.util.List;

public final class RealGetSubjectTelemetryCommand implements GetSubjectTelemetryCommand {
	private final RealCommandState state;

	public RealGetSubjectTelemetryCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		return state.getSubjectTelemetry(authToken, twinId, subjectId, historyPoints);
	}
}
