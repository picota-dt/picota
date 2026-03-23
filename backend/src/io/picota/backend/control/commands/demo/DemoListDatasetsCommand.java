package io.picota.backend.control.commands.demo;

import io.picota.backend.control.commands.ListDatasetsCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

import java.util.List;

public final class DemoListDatasetsCommand implements ListDatasetsCommand {
	private final DemoCommandState state;

	public DemoListDatasetsCommand(DemoCommandState state) {
		this.state = state;
	}

	@Override
	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		return state.listDatasets(authToken, twinId);
	}
}
