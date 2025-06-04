package io.picota.digitaltwin.control.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.trainvariablescommand.RuntimeCodeGenerator;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.model.DigitalTwin.TrainingReport.Variable;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future.State;
import java.util.stream.Collectors;

import static io.picota.digitaltwin.control.utils.Utils.toDouble;
import static io.picota.digitaltwin.model.DigitalTwin.State.Training;
import static io.picota.digitaltwin.model.MetadataFields.OUT_MAX;
import static io.picota.digitaltwin.model.MetadataFields.OUT_MIN;

public class TrainSubjectsCommand implements Command {
	private final DigitalTwinBox box;
	private final String digitalTwinId;
	private final File pythonVenv;

	public TrainSubjectsCommand(DigitalTwinBox box, String digitalTwinId) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
		this.pythonVenv = new File(box.configuration().pythonVenv());
	}

	@Override
	public Result execute() {
		DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		try {
			new RuntimeCodeGenerator(digitalTwin).generateTrainer();
			DigitalTwin.TrainingReport report = train(digitalTwin);
			digitalTwin.report(report);
			box.store().save();
			return Command.success();
		} catch (Throwable e) {
			Logger.error(e);
			removeAllData(digitalTwin);
			digitalTwin.state(DigitalTwin.State.TrainFinished);
			digitalTwin.progressMessage("Error during building process.\n" + e.getMessage());
			return new Result(false, "Error during building process.\n" + e.getMessage());
		}
	}

	private DigitalTwin.TrainingReport train(DigitalTwin digitalTwin) throws IOException, InterruptedException {
		File dtDirectory = digitalTwin.archetype().dir();
		digitalTwin.progressMessage("Training subjects...");
		Logger.info("Training subjects of " + digitalTwin.id() + "...");
		digitalTwin.state(Training);
		DigitalTwin.TrainingReport result = runTrain(digitalTwin, dtDirectory);
		Logger.info("Training subjects of " + digitalTwin.id() + ": Done");
		digitalTwin.progressMessage("Finished training. State: " + result.state());
		digitalTwin.state(DigitalTwin.State.TrainFinished);
		if (result.state().equals(State.SUCCESS)) digitalTwin.state(DigitalTwin.State.Prepared);
		new TrainReportBuilder().generate(result, digitalTwin.archetype().reportFile());
		return result;
	}

	private DigitalTwin.TrainingReport runTrain(DigitalTwin digitalTwin, File dtDirectory) throws IOException, InterruptedException {
		File scripts = digitalTwin.archetype().trainerScriptsDirectory();
		File modelsDir = digitalTwin.archetype().trainedVariablesDirectory();
		File dataDir = digitalTwin.archetype().dataDirectory();
		modelsDir.mkdirs();
		File scriptPath = new File(dtDirectory, "scripts/trainer/main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonVenv.getAbsolutePath() + "/bin/python",
				scriptPath.getAbsolutePath(),
				dataDir.getCanonicalPath(),
				modelsDir.getAbsolutePath())
				.directory(scripts)
				.start();
		int code = process.waitFor();
		String report = new String(process.getInputStream().readAllBytes());
		String errors = new String(process.getErrorStream().readAllBytes()).lines().filter(l -> l.contains("UserWarning")).collect(Collectors.joining("\n"));
		cleanData(digitalTwin.archetype());
		return new DigitalTwin.TrainingReport(dtDirectory.getName(), code == 0 ? State.SUCCESS : State.FAILED, report, errors, trainedVariables(digitalTwin, code, report), modelsDir);
	}

	private List<Variable> trainedVariables(DigitalTwin digitalTwin, int code, String report) {
		return code != 0 ? List.of() : report.lines().map(l -> variable(digitalTwin, l.split("\t"))).toList();
	}

	private void cleanData(Archetype archetype) {
		try {
			FileUtils.listFiles(archetype.dataDirectory(), new String[]{".jsonl", ".csv", ".tsv"}, true).forEach(File::delete);
			FileUtils.deleteDirectory(archetype.rawDataDirectory());
			FileUtils.deleteDirectory(archetype.tempDirectory());
			FileUtils.deleteDirectory(archetype.scriptsDirectory());
		} catch (IOException e) {
			Logger.error(e);
		}

	}

	private static void removeAllData(DigitalTwin digitalTwin) {
		try {
			FileUtils.deleteDirectory(digitalTwin.archetype().dataDirectory());
			FileUtils.deleteDirectory(digitalTwin.archetype().tempDirectory());
		} catch (IOException e) {
			Logger.error(e.getMessage());
		}
	}

	private Variable variable(DigitalTwin digitalTwin, String[] fields) {
		JsonObject metadata = metadata(digitalTwin, fields);
		return new Variable(fields[0], fields[1], toDouble(fields[2]), metadata.get(OUT_MIN).getAsDouble(), metadata.get(OUT_MAX).getAsDouble(), contributors(fields));
	}

	private static JsonObject metadata(DigitalTwin digitalTwin, String[] fields) {
		try {
			File file = digitalTwin.archetype().metadataFile(fields[0], fields[1]);
			return new Gson().fromJson(new FileReader(file), JsonObject.class);
		} catch (FileNotFoundException e) {
			return new JsonObject();
		}
	}

	private static Map<String, Double> contributors(String[] fields) {
		if (fields.length < 4) return Collections.emptyMap();
		Map<String, Double> collect = Arrays.stream(fields[3].split(",", -1))
				.map(t -> t.trim().split("###"))
				.collect(Collectors.toMap(t -> t[0].trim(), t -> toDouble(t[1].trim())));
		return merge(collect);
	}

	private static Map<String, Double> merge(Map<String, Double> original) {
		Map<String, Double> merged = new HashMap<>();
		original.forEach((key, value) -> {
			merged.merge(key.endsWith("_sin") || key.endsWith("_cos") ? key.substring(0, key.length() - 4) : key, value, Double::sum);
		});
		return merged;
	}


}
