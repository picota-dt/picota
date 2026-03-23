package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.VariableTelemetry;

import java.util.List;

@FunctionalInterface
public interface GetSubjectTelemetryCommand {
	List<VariableTelemetry> getSubjectTelemetry(String authToken, String twinId, String subjectId, int historyPoints);
}
