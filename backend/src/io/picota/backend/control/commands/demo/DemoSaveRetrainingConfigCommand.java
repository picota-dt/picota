package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.SaveRetrainingConfigCommand;
import io.picota.backend.control.ui.schemas.RetrainingConfig;

public final class DemoSaveRetrainingConfigCommand implements SaveRetrainingConfigCommand {
	private final DemoCommandState state;

	public DemoSaveRetrainingConfigCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		return state.saveRetrainingConfig(authToken, twinId, request);
	}
}
