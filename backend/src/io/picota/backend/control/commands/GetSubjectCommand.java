package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalSubject;

@FunctionalInterface
public interface GetSubjectCommand {
	DigitalSubject getSubject(String authToken, String twinId, String subjectId);
}
