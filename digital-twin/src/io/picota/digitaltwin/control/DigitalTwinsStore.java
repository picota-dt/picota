package io.picota.digitaltwin.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.CommandFactory;
import io.picota.digitaltwin.control.commands.ReadModelCommand;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.DigitalTwin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DigitalTwinsStore {
	private final File store;
	private final Gson gson;
	private Map<String, DigitalTwin> digitalTwins;

	public DigitalTwinsStore(File store) {
		this.store = store;
		this.gson = new GsonBuilder()
				.registerTypeAdapter(Instant.class, new Utils.InstantAdapter())
				.registerTypeAdapter(File.class, new Utils.FileAdapter())
				.create();
		store.mkdirs();
	}

	public void load(DigitalTwinBox box) {
		this.digitalTwins = Arrays.stream(Objects.requireNonNull(store.listFiles((d, name) -> name.endsWith(".json"))))
				.map(DigitalTwinsStore::read)
				.map(t -> gson.fromJson(t, DigitalTwin.class))
				.collect(Collectors.toMap(DigitalTwin::id, dt -> dt));
		for (DigitalTwin twin : digitalTwins.values()) {
			new CommandFactory(box).build(ReadModelCommand.class, twin.url()).execute();
		}
	}

	public DigitalTwin get(String id) {
		return digitalTwins.get(id);
	}

	public void add(DigitalTwin digitalTwin) {
		this.digitalTwins.put(digitalTwin.id(), digitalTwin);
		save(digitalTwin, gson.toJson(digitalTwin));
	}

	public void save() {
		digitalTwins.values().forEach(digitalTwin -> save(digitalTwin, gson.toJson(digitalTwin)));
	}

	private void save(DigitalTwin digitalTwin, String json) {
		try {
			Files.writeString(new File(store, digitalTwin.id() + ".json").toPath(), json);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	public void removeDigitalTwin(DigitalTwin digitalTwin) {
		new File(store, digitalTwin.id()).delete();
	}

	private static String read(File f) {
		try {
			return Files.readString(f.toPath());
		} catch (IOException e) {
			return "";
		}
	}
}