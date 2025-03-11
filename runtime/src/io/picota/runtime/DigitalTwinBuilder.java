package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.TimelineStore;
import io.picota.runtime.DigitalTwinBuilder.Result.Training;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import static io.picota.runtime.CsvUtils.*;

public class DigitalTwinBuilder {
	private final DataHubBox box;
	private final File sourcesDir;
	private final File pythonVenv;
	private final File modelsDir;
	private final File dataDir;
	private final ExecutorService executor;
	private Future<?> current;

	public DigitalTwinBuilder(DataHubBox box, File workingDir, File pythonVenv) {
		this.box = box;
		this.modelsDir = new File(workingDir, "models");
		this.sourcesDir = new File(workingDir, "sources");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		if (this.sourcesDir.exists()) clean();
		this.sourcesDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
		this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			int counter = 0;

			@Override
			public Thread newThread(@NotNull Runnable r) {
				Thread thread = new Thread(r, "Trainer-" + counter++);
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	public Future<?> start(OnFinished onFinished) {
		if (current != null && !current.isDone()) throw new IllegalStateException("Executor is not terminated");
		current = this.executor.submit(() -> {
			try {
				for (Sensor dt : box.graph().sensorList()) {
					Logger.info("Preparing data for " + dt.name$() + "...");
					prepareData(dt, timeline(dt));
				}
				untar(this.getClass().getResourceAsStream("/trainer.tar"), sourcesDir);
				onFinished.onFinished(train());
				clean();
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
			}
		});
		return current;
	}

	private Result train() throws IOException, InterruptedException {
		Logger.info("Training digital twins...");
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(sourcesDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), dataDir.getAbsolutePath(), modelsDir.getAbsolutePath())
				.redirectErrorStream(true)
				.directory(sourcesDir)
				.start();
		int code = process.waitFor();
		Logger.info("Finished training of digital twins. Code: " + code);
		String report = new String(process.getInputStream().readAllBytes());
		return new Result(code, report, trainings(code, report));
	}

	@NotNull
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
		if (current != null) {
			current.cancel(true);
			clean();
		}
	}

	private TimelineStore timeline(Sensor dt) {
		return box.datamarts().get("master").timelineStore().get(dt.name$(), dt.name$());
	}

	private void prepareData(Sensor dt, TimelineStore tl) throws IOException {
		if (tl == null) return;
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dataDir, tl.id() + ".csv")))) {
			writeHeader(dt, tl, writer);
			writeValuesForTrain(dt, tl, writer);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static void writeHeader(Sensor dt, TimelineStore tl, BufferedWriter writer) throws IOException {
		writer.write(headerForBuild(dt, tl));
	}

	private void clean() {
		try {
			FileUtils.deleteDirectory(this.sourcesDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	public interface OnFinished {
		void onFinished(Result result);
	}
}
