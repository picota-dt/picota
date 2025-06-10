package io.picota.digitaltwin.control.commands;

import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;

import java.io.File;

public class ProvideReportCommand implements Command<File> {

	private final DigitalTwinBox box;
	private final String digitalTwinId;

	public ProvideReportCommand(DigitalTwinBox box, String digitalTwinId) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
	}

	@Override
	public Result<File> execute() {
		DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		File file = digitalTwin.archetype().reportFile();
		if (!file.exists()) return new Result<>(false, "Report not found", null);
		return new Result<>(true, "", file);
	}
}
