package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.SubjectDataset;

@FunctionalInterface
public interface UploadDatasetCommand {
	SubjectDataset uploadDataset(String authToken, String twinId, String subjectId, String fileName, byte[] content);
}
