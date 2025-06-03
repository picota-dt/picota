package io.picota.digitaltwin.control.commands;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.DigitalTwinBox;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TemporalColumns;
import io.picota.digitaltwin.model.Archetype;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;
import systems.intino.datamarts.subjectstore.view.history.format.HistoryFormat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.chronoUnitOf;
import static io.picota.digitaltwin.control.utils.Utils.periodOf;

public class EvaluateVariablesCommand implements Command {
	private final DigitalTwinBox box;
	private final String digitalTwinId;
	private final Map<String, String> record;
	private static final Object lock = new Object();
	private final File pythonVenv;

	public EvaluateVariablesCommand(DigitalTwinBox box, String digitalTwinId, Map<String, String> record) {
		this.box = box;
		this.digitalTwinId = digitalTwinId;
		this.record = record;
		this.pythonVenv = new File(box.configuration().pythonVenv());

	}

	@Override
	public Result execute() {
		io.picota.digitaltwin.model.DigitalTwin digitalTwin = box.store().get(digitalTwinId);
		if (digitalTwin == null) throw new IllegalArgumentException("Digital Twin not found");
		if (digitalTwin.graph() == null) throw new IllegalArgumentException("Digital Twin has no description model");
		List<Inference> inferences = new ArrayList<>();
		for (DigitalSubject subject : digitalTwin.graph().digitalTwin().digitalSubjectList()) {
			inferences.addAll(infer(subject, digitalTwin.archetype()));
		}
		return Command.success(inferences);
	}

	public List<Inference> infer(DigitalSubject twin, Archetype archetype) {
		synchronized (lock) {
			try {
				prepareData(twin, archetype);
				return inferSubject(twin, archetype);
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
				return List.of();
			}
		}
	}

	private void prepareData(DigitalSubject ds, Archetype archetype) {
		for (DigitalSubject.InferenceModel inferenceModel : ds.inferenceModelList()) {
			File dataFile = new File(archetype.dataDirectory(), ds.subject().name$() + "/" + inferenceModel.variable().name$() + ".csv");
			var scale = ds.resolution().scale();
			int resolution = ds.resolution().amount();
			TemporalAmount duration = scale.ordinal() < Scale.Day.ordinal() ? Duration.of(resolution, chronoUnitOf(scale)) : periodOf(resolution, chronoUnitOf(scale));
//				SubjectHistoryView.of(subjectHistory)
//						.from(subjectHistory.last().toString())
//						.with(historyFormat(inferenceModel, subjectHistory, duration)).export().onlyCompleteRows().to(new FileOutputStream(dataFile));
		}
	}

	private List<Inference> inferSubject(DigitalSubject dt, Archetype archetype) throws IOException, InterruptedException {
		Logger.info("Inferring digital twin: " + dt.subject().name$());
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(archetype.evaluatorScriptsDirectory(), dt.subject().name$() + ".py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), archetype.trainedVariablesDirectory().getAbsolutePath(), archetype.dataDirectory().getAbsolutePath())
				.directory(archetype.evaluatorScriptsDirectory())
				.redirectErrorStream(true)
				.start();
		String result = new String(process.getInputStream().readAllBytes());
		Logger.info("Finished evaluation of variable. Code: " + process.waitFor() + ". Result:\n" + result);
		if (process.exitValue() != 0) throw new IOException(result.trim());
		return result.lines()
				.map(line -> line.trim().split("\t"))
				.map(f -> denormalize(new Inference(dt, f[0], Double.parseDouble(f[1])))).toList();
	}

	private HistoryFormat historyFormat(DigitalSubject.InferenceModel model, SubjectHistory history, TemporalAmount duration) {
		HistoryFormat format = new HistoryFormat(new HistoryFormat.RowDefinition(history.first(), history.last(), duration));
		TemporalColumns.get().forEach(format::add);
		history.tags().forEach(t -> format.add(new ColumnDefinition(t, t).add(new MinMaxNormalizationFilter())));
		int lag = model.asType().lookBack();
		if (lag > 0) appendLagColumns(history, format, lag);
		return format;
	}

	private static void appendLagColumns(SubjectHistory history, HistoryFormat format, int lookback) {
		IntStream.range(1, lookback)
				.forEach(l -> {
					TemporalColumns.get().forEach(c -> format.add(c.add(new LagFilter(l))));
					history.tags().forEach(t -> format.add(new ColumnDefinition(t + "-" + lookback, t).add(new LagFilter(l))));
				});
	}

	private Inference denormalize(Inference i) {
		double min = 0;//TODO
		double max = 1;
		return new Inference(i.subject, i.variable, denormalize(i.value, min, max));
	}

	public static double denormalize(double x, double min, double max) {
		return x * (max - min) + min;
	}

	public record Inference(DigitalSubject subject, String variable, double value) {
	}
}
