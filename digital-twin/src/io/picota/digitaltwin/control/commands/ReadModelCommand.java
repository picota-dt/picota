package io.picota.digitaltwin.control.commands;

import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.picota.ModelReader;
import io.quassar.picota.PicotaGraph;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

import static io.picota.digitaltwin.control.utils.Utils.digitalTwinId;

public class ReadModelCommand implements Command {
	private final String url;
	private final DigitalTwinBox box;

	public ReadModelCommand(DigitalTwinBox box, String url) {
		this.box = box;
		this.url = url;
	}

	@Override
	public Result execute() {
		try {
			URI uri = new URI(url);
			String id = digitalTwinId(uri);
			DigitalTwin digitalTwin = box.store().get(id);
			if (digitalTwin != null && digitalTwin.graph() != null) return Command.success(digitalTwin);
			PicotaGraph graph = ModelReader.loadFromURL(uri.toURL());
			if (graph == null) return new Result(false, "Impossible to read model from " + url);
			if (digitalTwin == null) digitalTwin = create(id);
			box.store().add(digitalTwin.graph(graph));
			return Command.success(digitalTwin);
		} catch (Throwable e) {
			return new Result(false, "Impossible to read model from " + url + " due to " + e.getMessage());
		}
	}

	@NotNull
	private DigitalTwin create(String id) {
		return new DigitalTwin(box.workspaceDir(), url, id, "name", "1.0.0");
	}
}
