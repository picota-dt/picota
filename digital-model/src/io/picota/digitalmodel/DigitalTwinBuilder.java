package io.picota.digitalmodel;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitalmodel.DigitalTwinBuilder.Result.Training;
import io.picota.language.model.DigitalTwin;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LagFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.digitalmodel.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class DigitalTwinBuilder {
	private final File pythonVenv;
	private final SubjectHistoryVault subjectStore;
	private final File modelsDir;
	private final File dataDir;
	private final ExecutorService executor;
	private final File scriptsDir;
	private Future<?> current;

	public DigitalTwinBuilder(SubjectHistoryVault subjectStore, File workingDir, File pythonVenv) {
		this.subjectStore = subjectStore;
		this.modelsDir = new File(workingDir, "models");
		this.scriptsDir = new File(workingDir, "scripts/trainer");
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

	public void stop() {
		if (current != null) current.cancel(true);
	}

	private Result train(DigitalTwin dt) throws IOException, InterruptedException {
		Logger.info("Training " + dt.name$() + "...");
		Result result = runTrain();
		Logger.info("Finished training of " + dt.name$() + ". Code: " + result.code);
		return result;
	}

	public record Result(int code, String report, List<Training> trainings) {
		public record Training(String dt, String variable, double loss, String[] contributors) {
		}
	}

	private Result runTrain() throws IOException, InterruptedException {
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

	private SubjectHistory subject(DigitalTwin dt) {
		return subjectStore.open(dt.name$());
	}

	private void prepareData(DigitalTwin dt, SubjectHistory history) throws IOException {
		if (history == null) {
			Logger.warn("No data found for " + dt.name$());
			return;
		}
		ChronoUnit scale = dt.resolution().scale().chronoUnit();
		var timeHorizon = timeHorizon(dt);
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale).plus(dt.memory(), scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, scale))
				.period(period(dt.resolution()))
				.add(TemporalColumns.get())
				.add(history.tags().stream().map(DigitalTwinBuilder::columnOf).toList())
				.add(lagColumns(history, dt.memory()))
				.add(timeHorizonColumns(history, timeHorizon))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(new File(dataDir, history.name() + ".csv")));
	}

	private static List<ColumnDefinition> timeHorizonColumns(SubjectHistory history, int timeHorizon) {
		return history.tags().stream()
				.map(t -> columnOf(t + "+" + timeHorizon, t).add())
				.toList();
	}

	private static List<ColumnDefinition> lagColumns(SubjectHistory history, int lag) {
		return IntStream.range(1, lag + 1).boxed()
				.flatMap(l -> lagColumns(history.tags(), l))
				.toList();
	}

	private static Stream<ColumnDefinition> lagColumns(List<String> tags, int l) {
		return tags.stream().map(t -> new ColumnDefinition(t + "-" + l, t)
				.add(List.of(new LagFilter(l), new MinMaxNormalizationFilter())));
	}

	private static ColumnDefinition columnOf(String t) {
		return new ColumnDefinition(t, t + ".first").add(new MinMaxNormalizationFilter());
	}

	private static ColumnDefinition columnOf(String name, String source) {
		return new ColumnDefinition(name, source + ".first").add(new MinMaxNormalizationFilter());
	}

	private static int timeHorizon(DigitalTwin dt) {
		return dt.isPredictive() ? dt.asPredictive().timeHorizon() : 0;
	}

	private TemporalAmount period(DigitalTwin.Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.value(), scale.chronoUnit()) : periodOf(resolution.value(), scale.chronoUnit());
	}

	public interface OnFinished {
		void onFinished(Result result);
	}
}