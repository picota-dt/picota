package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.GoogleAuthConfigResponse;

@FunctionalInterface
public interface GetGoogleAuthConfigCommand {
	GoogleAuthConfigResponse getGoogleAuthConfig();
}
