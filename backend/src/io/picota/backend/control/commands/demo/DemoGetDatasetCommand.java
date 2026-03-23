package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.GetDatasetCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

public final class DemoGetDatasetCommand implements GetDatasetCommand {
	private final DemoCommandState state;

	public DemoGetDatasetCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		return state.getDataset(authToken, twinId, subjectId);
	}
}
