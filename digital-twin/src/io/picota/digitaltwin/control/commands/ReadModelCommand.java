package io.picota.digitaltwin.control.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.monentia.picota.PicotaGraph;
import io.quassar.monentia.picota.PicotaModel;
import io.quassar.monentia.picota.PicotaModel.Model;

import java.net.URI;

import static io.picota.digitaltwin.control.utils.Utils.digitalTwinId;

public class ReadModelCommand implements Command<DigitalTwin> {
	private final String url;
	private final DigitalTwinBox box;
	private final Gson gson;

	public ReadModelCommand(DigitalTwinBox box, String url) {
		this.box = box;
		this.url = url;
		this.gson = new Gson();
	}

	@Override
	public Result<DigitalTwin> execute() {
		try {
			URI uri = new URI(url);
			String id = digitalTwinId(uri);
			DigitalTwin digitalTwin = box.store().get(id);
			Model model = PicotaModel.download(uri.toURL().toString());
			if (digitalTwin == null) digitalTwin = create(id, model);
			PicotaGraph graph = model.graph();
			if (graph != null) box.store().add(digitalTwin.graph(graph));
			else throw new IllegalArgumentException("Impossible to read model from " + url + ". " + model.parseLog());
			return new Result<>(true, "", digitalTwin);
		} catch (Throwable e) {
			e.printStackTrace();
			return new Result<>(false, "Impossible to read model from " + url + " due to " + e.getMessage());
		}
	}

	private DigitalTwin create(String id, Model model) {
		JsonObject object = gson.fromJson(model.metadata(), JsonObject.class);
		return new DigitalTwin(box.workspaceDir(), url, id, object.get("name").getAsString(), object.get("version").getAsString());
	}
}