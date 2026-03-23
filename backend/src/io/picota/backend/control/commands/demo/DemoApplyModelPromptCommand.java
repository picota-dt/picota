package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.ApplyModelPromptCommand;
import io.picota.backend.control.ui.schemas.ApplyModelPromptResponse;
import io.picota.backend.control.ui.schemas.requests.ApplyModelPromptRequest;

public final class DemoApplyModelPromptCommand implements ApplyModelPromptCommand {
	private final DemoCommandState state;

	public DemoApplyModelPromptCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public ApplyModelPromptResponse applyModelPrompt(String authToken, String twinId, ApplyModelPromptRequest request) {
		return state.applyModelPrompt(authToken, twinId, request);
	}
}
