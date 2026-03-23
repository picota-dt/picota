package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.UploadDatasetCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

public final class DemoUploadDatasetCommand implements UploadDatasetCommand {
	private final DemoCommandState state;

	public DemoUploadDatasetCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content) {
		return state.uploadDataset(authToken, twinId, subjectId, fileName, content);
	}
}
