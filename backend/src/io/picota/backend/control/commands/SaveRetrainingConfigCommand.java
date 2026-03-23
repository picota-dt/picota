package io.picota.backend.control.commands;

import io.picota.backend.control.ui.schemas.RetrainingConfig;

@FunctionalInterface
public interface SaveRetrainingConfigCommand {
	RetrainingConfig saveRetrainingConfig(String authToken, String twinId, RetrainingConfig request);
}
