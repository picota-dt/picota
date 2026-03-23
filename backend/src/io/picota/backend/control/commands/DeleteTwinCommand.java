package io.picota.backend.control.commands;

@FunctionalInterface
public interface DeleteTwinCommand {
	void deleteTwin(String authToken, String twinId);
}
