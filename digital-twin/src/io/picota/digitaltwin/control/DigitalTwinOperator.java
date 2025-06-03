package io.picota.digitaltwin.control;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TemporalColumns;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;
import systems.intino.datamarts.subjectstore.view.history.format.HistoryFormat;
import systems.intino.datamarts.subjectstore.view.history.format.HistoryFormat.RowDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.chronoUnitOf;
import static io.picota.digitaltwin.control.utils.Utils.periodOf;

public class DigitalTwinOperator {
	private final File scriptsDir;
	private final File pythonVenv;
	private final SubjectHistoryVault vault;
	private final File modelsDir;
	private final File dataDir;
	private final Object monitor = new Object();

	public DigitalTwinOperator(SubjectHistoryVault vault, File workingDir, File pythonVenv) {
		this.vault = vault;
		this.modelsDir = new File(workingDir, "models");
		this.scriptsDir = new File(workingDir, "scripts");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		this.scriptsDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
	}

	public List<Inference> infer(DigitalSubject twin) {
		synchronized (monitor) {
			try {
				return calculate(twin);
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
				return List.of();
			}
		}
	}

	private List<Inference> calculate(DigitalSubject subject) throws IOException, InterruptedException {
		SubjectHistory history = vault.open(subject.name$());
		if (history == null || history.last() == null) return List.of();
		prepareData(subject, history);
		return inferSubject(subject, history);
	}

	private List<Inference> inferSubject(DigitalSubject dt, SubjectHistory subject) throws IOException, InterruptedException {
		Logger.info("Inferring digital twin: " + dt.name$());
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(scriptsDir, dt.name$() + ".py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), modelsDir.getAbsolutePath(), dataDir.getAbsolutePath())
				.directory(scriptsDir)
				.redirectErrorStream(true)
				.start();
		String result = new String(process.getInputStream().readAllBytes());
		Logger.info("Finished evaluation of variable. Code: " + process.waitFor() + ". Result:\n" + result);
		if (process.exitValue() != 0) throw new IOException(result.trim());
		return result.lines()
				.map(line -> line.trim().split("\t"))
				.map(f -> denormalize(subject, new Inference(dt, f[0], Double.parseDouble(f[1])))).toList();
	}

	private void prepareData(DigitalSubject subject, SubjectHistory subjectHistory) {
		try {
			for (DigitalSubject.InferenceModel inferenceModel : subject.inferenceModelList()) {
				File dataFile = new File(dataDir, subject.name$() + "_" + inferenceModel.variable().name$() + ".csv");
				var scale = subject.resolution().scale();
				int resolution = subject.resolution().amount();
				TemporalAmount duration = scale.ordinal() < Scale.Day.ordinal() ? Duration.of(resolution, chronoUnitOf(scale)) : periodOf(resolution, chronoUnitOf(scale));
				SubjectHistoryView.of(subjectHistory)
						.from(subjectHistory.last().toString())
						.with(historyFormat(inferenceModel, subjectHistory, duration)).export().onlyCompleteRows().to(new FileOutputStream(dataFile));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private HistoryFormat historyFormat(DigitalSubject.InferenceModel model, SubjectHistory history, TemporalAmount duration) {
		HistoryFormat format = new HistoryFormat(new RowDefinition(history.first(), history.last(), duration));
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

	private Inference denormalize(SubjectHistory subject, Inference i) {
		var points = subject.query().number(i.variable);
		return new Inference(i.subject, i.variable, denormalize(i.value, points.all().distribution().min(), points.all().distribution().max()));
	}

	public static double denormalize(double x, double min, double max) {
		return x * (max - min) + min;
	}

	public record Inference(DigitalSubject subject, String variable, double value) {
	}
}