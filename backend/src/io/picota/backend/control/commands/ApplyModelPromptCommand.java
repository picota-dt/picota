package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.ApplyModelPromptResponse;
import io.picota.backend.control.ui.schemas.requests.ApplyModelPromptRequest;

@FunctionalInterface
public interface ApplyModelPromptCommand {
	ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request);
}
