package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.User;
import io.picota.backend.control.ui.schemas.requests.UpdateUserRequest;

@FunctionalInterface
public interface UpdateMeCommand {
	User updateMe(String authToken, UpdateUserRequest request);
}
