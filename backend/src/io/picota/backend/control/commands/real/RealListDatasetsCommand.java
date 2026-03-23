package io.picota.backend.control.commands.real;

import io.picota.backend.control.commands.ListDatasetsCommand;
import io.picota.backend.control.ui.schemas.SubjectDataset;

import java.util.List;

public final class RealListDatasetsCommand implements ListDatasetsCommand {
	private final RealCommandState state;

	public RealListDatasetsCommand(RealCommandState state) {
		this.state = state;
	}

	@Override
	public List<SubjectDataset> listDatasets(String authToken, String twinId) {
		return state.listDatasets(authToken, twinId);
	}
}
