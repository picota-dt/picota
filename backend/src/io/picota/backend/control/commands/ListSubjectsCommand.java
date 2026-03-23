package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.DigitalSubject;

import java.util.List;

@FunctionalInterface
public interface ListSubjectsCommand {
	List<DigitalSubject> listSubjects(String authToken, String twinId);
}
