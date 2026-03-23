package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetSubjectTelemetryCommand;
import io.picota.backend.control.ui.schemas.VariableTelemetry;

import java.util.List;

public final class DemoGetSubjectTelemetryCommand implements GetSubjectTelemetryCommand {
	private final DemoCommandState state;

	public DemoGetSubjectTelemetryCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints) {
		return state.getSubjectTelemetry(authToken, twinId, subjectId, historyPoints);
	}
}
