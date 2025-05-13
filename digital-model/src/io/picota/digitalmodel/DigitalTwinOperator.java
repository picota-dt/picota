package io.picota.digitalmodel;

import io.intino.alexandria.logger.Logger;
import io.picota.language.model.DigitalTwin;
import io.picota.language.model.rules.Scale;
import org.apache.commons.io.FileUtils;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.SubjectStore;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.model.Subject;
import systems.intino.datamarts.subjectstore.view.format.history.ColumnDefinition;
import systems.intino.datamarts.subjectstore.view.format.history.HistoryFormat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.stream.IntStream;

import static io.picota.digitalmodel.utils.Utils.periodOf;

public class DigitalTwinOperator {
	private final File scriptsDir;
	private final File pythonVenv;
	private final SubjectStore store;
	private final File modelsDir;
	private final File dataDir;
	private final Object monitor = new Object();

	public DigitalTwinOperator(SubjectStore store, File workingDir, File pythonVenv) {
		this.store = store;
		this.modelsDir = new File(workingDir, "models");
		this.scriptsDir = new File(workingDir, "scripts");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		this.scriptsDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
	}

	public List<Inference> infer(DigitalTwin twin) {
		synchronized (monitor) {
			return calculate(twin);
		}
	}

	private List<Inference> calculate(DigitalTwin digitalTwin) {
		try {
			Subject subject = store.open(digitalTwin.name$());
			if (subject == null || subject.history().last() == null) return List.of();
			return inferDt(digitalTwin, subject, prepareData(digitalTwin, subject));
		} catch (Exception e) {
			Logger.error(e);
			return List.of();
		}
	}

	private List<Inference> inferDt(DigitalTwin dt, Subject subject, File data) throws IOException, InterruptedException {
		Logger.info("Inferring digital twin: " + dt.name$());
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(scriptsDir, dt.name$() + ".py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), modelsDir.getAbsolutePath(), data.getAbsolutePath())
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

	private File prepareData(DigitalTwin dt, Subject data) {
		try {
			File dataFile = new File(dataDir, dt.name$() + ".csv");
			var scale = dt.resolution().scale();
			int resolution = dt.resolution().value();
			TemporalAmount duration = scale.ordinal() < Scale.Day.ordinal() ? Duration.of(resolution, scale.chronoUnit()) : periodOf(resolution, scale.chronoUnit());
			SubjectHistoryView.of(data.history())
					.from(data.history().last().minus(dt.memory(), dt.resolution().scale().chronoUnit()).toString())
					.with(historyFormat(dt, data.history(), duration)).exportTo(dataFile);
			return dataFile;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private HistoryFormat historyFormat(DigitalTwin model, SubjectHistory history, TemporalAmount duration) {
		HistoryFormat format = new HistoryFormat(history.first(), history.last(), duration);
		TemporalMappers.mappers.forEach((k, v) -> format.add(new ColumnDefinition(k, v)));
		history.tags().forEach(t -> format.add(new ColumnDefinition(t, t).add(new MinMaxNormalizationFilter())));
		int lag = model.memory();
		if (lag > 0) appendLagColumns(history, format, lag);
		return format;
	}

	private static void appendLagColumns(SubjectHistory history, HistoryFormat format, int lag) {
		IntStream.range(1, lag).forEach(l -> history.tags().forEach(t -> format.add(new ColumnDefinition(t + "-" + lag, t).add(List.of(new LagFilter(l), new MinMaxNormalizationFilter())))));
	}

	private Inference denormalize(Subject subject, Inference i) {
		var points = subject.history().query().number(i.variable);
		return new Inference(i.digitalTwin, i.variable, denormalize(i.value, points.all().distribution().min(), points.all().distribution().max()));
	}

	public static double denormalize(double x, double min, double max) {
		return x * (max - min) + min;
	}

	public record Inference(DigitalTwin digitalTwin, String variable, double value) {
	}
}