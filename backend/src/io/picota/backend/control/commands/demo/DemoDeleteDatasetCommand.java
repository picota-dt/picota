package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.DeleteDatasetCommand;

public final class DemoDeleteDatasetCommand implements DeleteDatasetCommand {
	private final DemoCommandState state;

	public DemoDeleteDatasetCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public void deleteDataset(String authToken, String twinId, String subjectId) {
		state.deleteDataset(authToken, twinId, subjectId);
	}
}
