package io.picota.digitaltwin.control.commands;

import io.intino.magritte.framework.Layer;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.utils.Compression;
import io.picota.digitaltwin.control.utils.Utils;
import io.picota.digitaltwin.model.DigitalTwin;
import io.quassar.picota.Reality;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.writeString;

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
		List<String> common = Utils.variableNamesOf(reality);
		Map<String, String> subjects = reality.subjectList().stream().collect(Collectors.toMap(Layer::name$, s -> String.join(",", merge(common, Utils.variableNamesOf(s)))));
		try {
			Path temp = Files.createTempDirectory(digitalTwin.name() + " template");
			for (Map.Entry<String, String> entry : subjects.entrySet()) {
				Reality.Subject subject = reality.subject(s -> s.name$().equals(entry.getKey()));
				if (subject.isPrototype()) for (int i = 1; i <= 3; i++)
					write(temp, entry.getKey() + "00" + i + ".csv", entry);
				else write(temp, entry.getKey() + ".csv", entry);
			}
			Path zip = Files.createTempFile(digitalTwin.name(), ".zip");
			Compression.zipDir(temp, zip);
			return new Result<>(true, "", zip.toFile());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void write(Path temp, String entry, Map.Entry<String, String> entry1) throws IOException {
		writeString(new File(temp.toFile(), entry).toPath(), entry1.getValue());
	}

	private List<String> merge(List<String> common, Stream<String> subjectNames) {
		ArrayList<String> names = new ArrayList<>(common);
		names.addFirst("instant");
		names.addAll(subjectNames.toList());
		return names;
	}

}
