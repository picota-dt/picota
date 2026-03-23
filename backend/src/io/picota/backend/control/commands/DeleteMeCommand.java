package io.picota.backend.control.commands;

@FunctionalInterface
public interface DeleteMeCommand {
	void deleteMe(String authToken);
}
