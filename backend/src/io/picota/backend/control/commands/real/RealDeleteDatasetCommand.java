package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.DeleteDatasetCommand;

public final class RealDeleteDatasetCommand implements DeleteDatasetCommand {
	private final RealCommandState state;

	public RealDeleteDatasetCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteDataset(String authToken, String twinId, String subjectId) {
		state.deleteDataset(authToken, twinId, subjectId);
	}
}
