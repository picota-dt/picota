package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.SaveRetrainingConfigCommand;
import io.picota.backend.control.ui.schemas.RetrainingConfig;

public final class RealSaveRetrainingConfigCommand implements SaveRetrainingConfigCommand {
	private final RealCommandState state;

	public RealSaveRetrainingConfigCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request) {
		return state.saveRetrainingConfig(authToken, twinId, request);
	}
}
