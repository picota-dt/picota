package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.SaveModelResponse;
import io.picota.backend.control.ui.schemas.requests.SaveModelRequest;

@FunctionalInterface
public interface SaveModelCommand {
	SaveModelResponse saveModel(String authToken, String twinId, SaveModelRequest request);
}
