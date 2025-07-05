package io.picota.digitaltwin.control.commands;

import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;

public class ReadDigitalTwinCommand implements Command<Map<String, ? extends Serializable>> {
	private final DigitalTwinBox box;
	private final String id;

	public ReadDigitalTwinCommand(DigitalTwinBox box, String id) {
		this.box = box;
		this.id = id;
	}

	@Override
	public Result<Map<String, ? extends Serializable>> execute() {
		try {
			DigitalTwin digitalTwin = box.store().get(id);
			if (digitalTwin == null) return new Result<>(false, "DigitalTwin not found");
			Map<String, ? extends Serializable> response = response(digitalTwin);
			return new Result<>(true, "", response);
		} catch (Throwable e) {
			return new Result<>(false, "Impossible to find Digital Twin " + id + " due to " + e.getMessage());
		}
	}

	@NotNull
	private static Map<String, ? extends Serializable> response(DigitalTwin digitalTwin) {
		return Map.of("id", digitalTwin.id(),
				"url", digitalTwin.url(),
				"model", digitalTwin.graph() != null,
				"name", digitalTwin.name(),
				"version", digitalTwin.version(),
				"dataset", digitalTwin.archetype().hasRawData(),
				"progress", digitalTwin.progress(),
				"progressMessage", digitalTwin.progressMessage() == null ? "" : digitalTwin.progressMessage(),
				"state", digitalTwin.state().name());
	}
}
