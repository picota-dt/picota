package io.picota.digitaltwin.control.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.trainvariablescommand.RuntimeCodeGenerator;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder.DataSheetReport;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TrainReportBuilder.TrainedSubject;
import io.picota.digitaltwin.model.Archetype;
import io.picota.digitaltwin.model.DigitalTwin;
import io.picota.digitaltwin.model.DigitalTwin.TrainingReport;
import io.picota.digitaltwin.model.DigitalTwin.TrainingReport.Variable;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Future.State;
import java.util.stream.Collectors;

import static io.picota.digitaltwin.control.utils.Utils.toDouble;
import static io.picota.digitaltwin.model.DigitalTwin.State.*;
import static io.picota.digitaltwin.model.MetadataFields.OUT_MAX;
import static io.picota.digitaltwin.model.MetadataFields.OUT_MIN;
import static java.util.concurrent.Future.State.SUCCESS;

public class TrainSubjectsCommand implements Command<Void> {
	private final DigitalTwinBox box;
	private final String digitalTwinId;
	private final File pythonVenv;
	private final DecimalFormat df;

	public TrainSubjectsCommand(DigitalTwinBox box, String digitalTwinId) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
		this.pythonVenv = new File(box.configuration().pythonVenv());
		df = new DecimalFormat("#.00");
		df.setRoundingMode(RoundingMode.HALF_UP);  // redondeo “clásico”
	}

	@Override
	public Result<Void> execute() {
		DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		EmailNotifier notifier = new EmailNotifier(digitalTwin, box.configuration().emailConfFile());
		try {
			digitalTwin.progressMessage("Preparing data for build subjects...");
			new RuntimeCodeGenerator(digitalTwin).generateTrainer();
			TrainingReport report = train(digitalTwin);
			digitalTwin.report(report);
			box.store().save();
			notifier.notifyExecution(report);
			return Command.success();
		} catch (IllegalArgumentException e) {
			notifyError(digitalTwin, notifier, e.getMessage());
			removeAllData(digitalTwin);
		} catch (Throwable e) {
			Logger.error(e);
			notifyError(digitalTwin, notifier, e.getMessage());
		}
		return new Result<>(false, digitalTwin.progressMessage());
	}

	private static void notifyError(DigitalTwin digitalTwin, EmailNotifier notifier, String error) {
		digitalTwin.progressMessage("Error during building process.\n" + error).state(TrainFinished);
		notifier.notifyFailedExecution();
		removeAllData(digitalTwin);
	}

	private TrainingReport train(DigitalTwin digitalTwin) throws IOException, InterruptedException {
		File dtDirectory = digitalTwin.archetype().dir();
		digitalTwin.progressMessage("Training subjects...");
		Logger.info("Training subjects of " + digitalTwin.id() + "...");
		digitalTwin.state(Training);
		TrainingReport result = runTrain(digitalTwin, dtDirectory);
		Logger.info("Training subjects of " + digitalTwin.id() + ": Done");
		digitalTwin.progressMessage("Finished training. State: " + result.state());
		digitalTwin.state(TrainFinished);
		if (result.state().equals(SUCCESS)) digitalTwin.state(Prepared);
		new TrainReportBuilder().generate(map(digitalTwin, result), digitalTwin.archetype().reportFile());
		return result;
	}

	private DataSheetReport map(DigitalTwin digitalTwin, TrainingReport result) {
		return new DataSheetReport(digitalTwinId, digitalTwin.graph().digitalTwin().name$(), digitalTwin.createdAt(), digitalTwin.url(), subjects(result, digitalTwin));
	}

	private List<TrainedSubject> subjects(TrainingReport result, DigitalTwin digitalTwin) {
		Map<String, List<Variable>> variablesBySubject = result.trainings().stream().collect(Collectors.groupingBy(Variable::dt));
		return variablesBySubject.keySet().stream()
				.map(subject -> new TrainedSubject(subject, variablesBySubject.get(subject).stream().map(this::map).toList()))
				.collect(Collectors.toList());
	}

	private TrainReportBuilder.Inference map(Variable variable) {
		int horizon = !variable.name().contains("+") ?
				0 : Integer.parseInt(variable.name().substring(variable.name().lastIndexOf("+") + 1));
		return new TrainReportBuilder.Inference(variableName(variable), horizon, "", df.format(variable.loss() * 100), variable.contributors());
	}

	private static String variableName(Variable variable) {
		return variable.name().contains("+") ? variable.name().substring(0, variable.name().lastIndexOf("+")) : variable.name();
	}

	private TrainingReport runTrain(DigitalTwin digitalTwin, File dtDirectory) throws IOException, InterruptedException {
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
		StringBuilder report = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
		readOutput(digitalTwin, reader, report);
		int exitValue = process.waitFor();
		String reportResult = report.toString();
		cleanData(digitalTwin.archetype());
		return new TrainingReport(dtDirectory.getName(), exitValue == 0 ? SUCCESS : State.FAILED, reportResult, errorMessages(process), trainedVariables(digitalTwin, exitValue, reportResult), modelsDir);
	}

	@NotNull
	private static String errorMessages(Process process) throws IOException {
		return new String(process.getErrorStream().readAllBytes()).lines().filter(l -> !l.contains("UserWarning")).collect(Collectors.joining("\n"));
	}

	private void readOutput(DigitalTwin digitalTwin, BufferedReader reader, StringBuilder report) {
		new Thread(() -> {
			String line;
			try {
				while ((line = reader.readLine()) != null) {
					report.append(line).append("\n");
					digitalTwin.progressMessage("processed " + variable(digitalTwin, line.split("\t")).name());
					System.out.println(line);
				}
			} catch (IOException e) {
				Logger.error(e);
			}
		}).start();
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