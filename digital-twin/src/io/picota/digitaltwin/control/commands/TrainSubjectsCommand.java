package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.trainvariablescommand.DataPreparer;
import io.picota.digitaltwin.control.commands.trainvariablescommand.RuntimeCodeGenerator;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.control.utils.Utils;
import io.quassar.picota.DigitalTwin.DigitalSubject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future.State;
import java.util.stream.Collectors;

import static io.picota.digitaltwin.control.utils.Utils.toDouble;
import static io.picota.digitaltwin.model.DigitalTwin.State.Training;
import static org.apache.commons.io.FilenameUtils.removeExtension;

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
			Map<DigitalSubject, List<String>> subjectTargets = new HashMap<>();
			for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList())
				subjectTargets.put(subject, prepareDigitalSubject(digitalTwin, subject));
			new RuntimeCodeGenerator(digitalTwin, subjectTargets).generate();
			DigitalTwin.TrainingReport report = train(digitalTwin);
			digitalTwin.report(report);
			box.store().save();
			return Command.success();
		} catch (IOException | InterruptedException | IllegalArgumentException e) {
			Logger.error(e.getMessage());
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
		DigitalTwin.TrainingReport result = runTrain(dtDirectory);
		Logger.info("Training subjects of " + digitalTwin.id() + ": Done");
		digitalTwin.progressMessage("Finished training. State: " + result.state());
		digitalTwin.state(DigitalTwin.State.TrainFinished);
		if (result.state().equals(State.SUCCESS)) digitalTwin.state(DigitalTwin.State.Prepared);
		new TrainReportBuilder().generate(result, digitalTwin.archetype().reportFile());
		return result;
	}

	private DigitalTwin.TrainingReport runTrain(File dtDirectory) throws IOException, InterruptedException {
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scripts = new File(dtDirectory, "scripts");
		File modelsDir = new File(dtDirectory, "models");
		modelsDir.mkdirs();
		File scriptPath = new File(dtDirectory, "scripts/trainer/main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), new File(dtDirectory, "data").getCanonicalPath(), modelsDir.getAbsolutePath())
				.directory(scripts)
				.start();
		int code = process.waitFor();
		String report = new String(process.getInputStream().readAllBytes());
		String errors = new String(process.getErrorStream().readAllBytes());
		System.out.println(report);
		System.out.println(errors);
		return new DigitalTwin.TrainingReport(dtDirectory.getName(), code == 0 ? State.SUCCESS : State.FAILED, report, errors, trainedVariables(code, report), modelsDir);
	}


	private List<DigitalTwin.TrainingReport.Variable> trainedVariables(int code, String report) {
		return code != 0 ? List.of() : report.lines().map(l -> variable(l.split("\t"))).toList();
	}

	private DigitalTwin.TrainingReport.Variable variable(String[] fields) {
		return new DigitalTwin.TrainingReport.Variable(fields[0], fields[1], toDouble(fields[2]), contributors(fields));
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

	private List<String> prepareDigitalSubject(DigitalTwin digitalTwin, DigitalSubject subject) throws IOException {
		List<File> files = findFiles(digitalTwin.archetype(), subject);
		for (File subjectDataset : files) prepareDataOf(digitalTwin.archetype(), subject, subjectDataset);
		return files.stream().map(f -> removeExtension(f.getName())).toList();
	}

	private List<File> findFiles(Archetype archetype, DigitalSubject ds) {
		File rawDataDir = archetype.rawDataDirectory();
		if (ds.subject().isPrototype()) return Utils.getFilesWithPrefix(rawDataDir, ds.subject().name$());
		else {
			File file = new File(rawDataDir, ds.subject().name$() + ".csv");
			return Collections.singletonList(file.exists() ? file : new File(rawDataDir, ds.subject().name$() + ".tsv"));
		}
	}

	private void prepareDataOf(Archetype archetype, DigitalSubject subject, File subjectDataset) throws IOException {
		for (DigitalSubject.InferenceModel inferenceModel : subject.inferenceModelList())
			if (!subjectDataset.exists() || subjectDataset.length() == 0)
				throw new IllegalArgumentException("Expected dataset " + subjectDataset.getName() + ", but it does not exist or is empty.");
			else new DataPreparer(archetype.tempDirectory(), archetype.dataDirectory())
					.prepareData(subject, inferenceModel, subjectDataset);
	}
}
