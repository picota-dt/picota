package io.picota.digitaltwin.control.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.picota.ModelParser;

import java.net.URI;

import static io.picota.digitaltwin.control.utils.Utils.digitalTwinId;

public class ReadModelCommand implements Command {
	private final String url;
	private final DigitalTwinBox box;
	private final Gson gson;

	public ReadModelCommand(DigitalTwinBox box, String url) {
		this.box = box;
		this.url = url;
		this.gson = new Gson();
	}

	@Override
	public Result execute() {
		try {
			URI uri = new URI(url);
			String id = digitalTwinId(uri);
			DigitalTwin digitalTwin = box.store().get(id);
			if (digitalTwin != null && digitalTwin.graph() != null) return Command.success(digitalTwin);
			ModelParser.Model model = ModelParser.loadFromURL(uri.toURL());
			if (model == null) return new Result(false, "Impossible to read model from " + url);
			if (digitalTwin == null) digitalTwin = create(id, model);
			box.store().add(digitalTwin.graph(model.graph()));
			return Command.success(digitalTwin);
		} catch (Throwable e) {
			return new Result(false, "Impossible to read model from " + url + " due to " + e.getMessage());
		}
	}

	private DigitalTwin create(String id, ModelParser.Model model) {
		JsonObject object = gson.fromJson(model.metadata(), JsonObject.class);
		return new DigitalTwin(box.workspaceDir(), url, id, object.get("name").getAsString(), object.get("version").getAsString());
	}
}
