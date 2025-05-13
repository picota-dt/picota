package io.picota.digitalmodel;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.DigitalTwinBuilder.Result.Training;
import io.picota.language.model.DigitalTwin;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.SubjectStore;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.format.history.ColumnDefinition;
import systems.intino.datamarts.subjectstore.view.format.history.HistoryFormat;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;

import static io.picota.digitalmodel.utils.Utils.periodOf;

public class DigitalTwinBuilder {
	private final File pythonVenv;
	private final SubjectStore subjectStore;
	private final File modelsDir;
	private final File dataDir;
	private final ExecutorService executor;
	private final File scriptsDir;
	private Future<?> current;

	public DigitalTwinBuilder(SubjectStore subjectStore, File workingDir, File pythonVenv) {
		this.subjectStore = subjectStore;
		this.modelsDir = new File(workingDir, "models");
		this.scriptsDir = new File(workingDir, "scripts");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
		this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			int counter = 0;

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, "Trainer-" + counter++);
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	public Future<?> build(DigitalTwin dt, OnFinished onFinished) {
		if (current != null && !current.isDone()) throw new IllegalStateException("Executor is not terminated");
		current = this.executor.submit(() -> {
			try {
				prepareData(dt, subject(dt));
				onFinished.onFinished(train(dt));
			} catch (Throwable e) {
				Logger.error(e);
			}
		});
		return current;
	}

	private Result train(DigitalTwin dt) throws IOException, InterruptedException {
		Logger.info("Training " + dt.name$() + "...");
		Result result = prepareData();
		Logger.info("Finished training of " + dt.name$() + ". Code: " + result.code);
		return result;
	}

	private Result prepareData() throws IOException, InterruptedException {
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(scriptsDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), dataDir.getAbsolutePath(), modelsDir.getAbsolutePath())
				.redirectErrorStream(true)
				.directory(scriptsDir)
				.start();
		int code = process.waitFor();
		String report = new String(process.getInputStream().readAllBytes());
		return new Result(code, report, trainings(code, report));
	}

	private List<Training> trainings(int code, String report) {
		return code != 0 ? List.of() : report.lines().map(l -> trainingResultOf(l.split("\t"))).toList();
	}

	private Training trainingResultOf(String[] fields) {
		return new Training(fields[0], fields[1], Double.parseDouble(fields[2]), Arrays.stream(fields).skip(3).toArray(String[]::new));
	}

	public record Result(int code, String report, List<Training> trainings) {
		public record Training(String dt, String variable, double loss, String[] contributors) {
		}
	}

	public void stop() {
		if (current != null) current.cancel(true);
	}

	private systems.intino.datamarts.subjectstore.model.Subject subject(DigitalTwin dt) {
		return subjectStore.open(dt.name$());
	}

	private void prepareData(DigitalTwin dt, systems.intino.datamarts.subjectstore.model.Subject data) throws IOException {
		if (data == null) {
			Logger.warn("No data found for " + dt.name$());
			return;
		}
		Logger.info("Preparing data for " + dt.name$() + "...");
		SubjectHistory history = data.history();
		int resolution = dt.resolution().value();
		io.picota.language.model.rules.Scale scale = dt.resolution().scale();
		TemporalAmount duration = scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution, scale.chronoUnit()) : periodOf(resolution, scale.chronoUnit());
		SubjectHistoryView view = SubjectHistoryView.of(history).
				with(historyFormat(dt, history, duration));
		view.exportTo(new File(dataDir, data.name() + ".csv"));
	}

	private static HistoryFormat historyFormat(DigitalTwin dt, SubjectHistory history, TemporalAmount duration) {
		HistoryFormat format = new HistoryFormat(history.first(), history.last(), duration);
		TemporalMappers.mappers.forEach((k, v) -> format.add(new ColumnDefinition(k, v)));
		history.tags().forEach(t -> format.add(new ColumnDefinition(t, t + ".first").add(new MinMaxNormalizationFilter())));
		int timeHorizon = dt.isPredictive() ? dt.asPredictive().timeHorizon() : 0;
		if (timeHorizon > 0) appendTimeHorizonColumns(history, format, timeHorizon);
		int lag = dt.memory();
		if (lag > 0) appendLagColumns(history, format, lag);
		return format;
	}

	private static void appendTimeHorizonColumns(SubjectHistory history, HistoryFormat format, int timeHorizon) {
		history.tags().forEach(t -> format.add(new ColumnDefinition(t + "+" + timeHorizon, timeHorizonDefinition(t)).add(new MinMaxNormalizationFilter())));
	}

	private static String timeHorizonDefinition(String t) {
		//TODO
		return t + ".first";
	}

	private static void appendLagColumns(SubjectHistory history, HistoryFormat format, int lag) {
		IntStream.range(1, lag).forEach(l -> history.tags().forEach(t -> format.add(new ColumnDefinition(t + "-" + lag, t).add(List.of(new LagFilter(l), new MinMaxNormalizationFilter())))));
	}

	public interface OnFinished {
		void onFinished(Result result);
	}
}
