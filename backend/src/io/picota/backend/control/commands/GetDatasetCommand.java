package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.SubjectDataset;

@FunctionalInterface
public interface GetDatasetCommand {
	SubjectDataset getDataset(String authToken, String twinId, String subjectId);
}
