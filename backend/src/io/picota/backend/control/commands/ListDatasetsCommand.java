package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.SubjectDataset;

import java.util.List;

@FunctionalInterface
public interface ListDatasetsCommand {
	List<SubjectDataset> listDatasets(String authToken, String twinId);
}
