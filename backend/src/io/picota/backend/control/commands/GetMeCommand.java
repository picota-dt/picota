package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.User;

@FunctionalInterface
public interface GetMeCommand {
	User getMe(String authToken);
}
