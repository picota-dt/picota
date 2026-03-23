package io.picota.backend.control.commands;

@FunctionalInterface
public interface DeleteDatasetCommand {
	void deleteDataset(String authToken, String twinId, String subjectId);
}
