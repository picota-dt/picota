package io.picota.backend.control.commands;

@FunctionalInterface
public interface LogoutCommand {
	void logout(String authToken);
}
