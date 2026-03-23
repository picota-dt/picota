package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.UploadDatasetCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

public final class RealUploadDatasetCommand implements UploadDatasetCommand {
	private final RealCommandState state;

	public RealUploadDatasetCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		return state.uploadDataset(authToken, twinId, subjectId, fileName, content);
	}
}
