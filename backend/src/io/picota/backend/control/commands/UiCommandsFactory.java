package io.picota.backend.control.commands;

import io.picota.backend.persistence.ModelPersistence;

public final class UiCommandsFactory {
	private UiCommandsFactory() {
	}

	public static UiCommands create(UiCommandsMode mode) {
		return create(mode, null);
	}

	public static UiCommands create(UiCommandsMode mode, ModelPersistence persistence) {
		return mode == UiCommandsMode.MOCK ? new MockUiCommands() : new RealUiCommands(persistence);
	}
}
