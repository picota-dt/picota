package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.ApplyModelPromptCommand;
import io.picota.backend.control.ui.schemas.ApplyModelPromptResponse;
import io.picota.backend.control.ui.schemas.requests.ApplyModelPromptRequest;

public final class RealApplyModelPromptCommand implements ApplyModelPromptCommand {
	private final RealCommandState state;

	public RealApplyModelPromptCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		return state.applyModelPrompt(authToken, twinId, request);
	}
}
