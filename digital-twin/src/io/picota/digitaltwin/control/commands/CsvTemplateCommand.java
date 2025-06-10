package io.picota.digitaltwin.control.commands;

import io.intino.magritte.framework.Layer;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.utils.Compression;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.picota.Reality;
import io.quassar.picota.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CsvTemplateCommand implements Command<File> {

	private final DigitalTwinBox box;
	private final String digitalTwinId;

	public CsvTemplateCommand(DigitalTwinBox box, String digitalTwinId) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
	}

	@Override
	public Result<File> execute() {
		DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		Reality reality = digitalTwin.graph().reality();
		List<String> common = reality.variableList().stream().flatMap(this::namesOf).collect(Collectors.toList());
		Map<String, String> subjects = reality.subjectList().stream().collect(Collectors.toMap(Layer::name$, s -> String.join(",", merge(common, namesOf(s)))));
		try {
			Path temp = Files.createTempDirectory(digitalTwin.name() + " template");
			for (Map.Entry<String, String> entry : subjects.entrySet())
				Files.writeString(new File(temp.toFile(), entry.getKey() + ".csv").toPath(), entry.getValue());
			Path zip = Files.createTempFile(digitalTwin.name(), ".zip");
			Compression.zipDir(temp, zip);
			return new Result<>(true, "", zip.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<String> merge(List<String> common, Stream<String> subjectNames) {
		ArrayList<String> names = new ArrayList<>(common);
		names.addAll(subjectNames.toList());
		return names;
	}

	private Stream<String> namesOf(Variable v) {
		if (v.isComposite())
			return v.asComposite().componentsList().stream().flatMap(c -> c.values().stream()).map(c -> v.name$() + ":" + c);
		return Stream.of(v.name$());
	}

	private Stream<String> namesOf(Reality.Subject s) {
		return s.variableList().stream().flatMap(this::namesOf);
	}
}
