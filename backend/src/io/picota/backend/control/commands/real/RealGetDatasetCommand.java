package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.GetDatasetCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

public final class RealGetDatasetCommand implements GetDatasetCommand {
	private final RealCommandState state;

	public RealGetDatasetCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public SubjectDataset getDataset(String authToken, String twinId, String subjectId) {
		return state.getDataset(authToken, twinId, subjectId);
	}
}
