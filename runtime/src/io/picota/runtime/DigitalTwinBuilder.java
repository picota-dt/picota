package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.TimelineStore;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.runtime.Utils.*;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

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
				untar(this.getClass().getResourceAsStream("/trainer.tar"));
				String report = train();
//				clean();
				onFinished.onFinished(report);
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
			}
		});
		return current;
	}

	private String train() throws IOException, InterruptedException {
		Logger.info("Training digital twins...");
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(sourcesDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), dataDir.getAbsolutePath(), modelsDir.getAbsolutePath())
				.redirectErrorStream(true)
				.directory(sourcesDir)
				.start();
		Logger.info("Finished training of digital twins. Code: " + process.waitFor());
		return new String(process.getInputStream().readAllBytes());
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
			writer.write(header(dt, tl));
			writeValues(dt, tl, writer);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private String header(Sensor dt, TimelineStore tl) {
		StringBuilder header = new StringBuilder();
		header.append(dateTimeLabels());
		header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
				.flatMap(this::labelOf)
				.collect(joining(SEPARATOR)));
		int timeHorizon = timeHorizon(dt);
		if (timeHorizon > 0) appendTimeHorizonLabels(tl, header, timeHorizon);
		appendLagLabels(dt, tl, header);
		return header + "\n";
	}

	private void appendLagLabels(Sensor dt, TimelineStore tl, StringBuilder header) {
		IntStream.range(0, lag(dt)).forEach(i ->
				header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
						.flatMap(this::labelOf)
						.map(label -> format("%s-%d", label, i))
						.collect(joining(SEPARATOR))));
	}

	private void appendTimeHorizonLabels(TimelineStore tl, StringBuilder header, int timeHorizon) {
		header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
				.flatMap(this::labelOf)
				.map(label -> format("%s+%d", label, timeHorizon))
				.collect(joining(SEPARATOR)));
	}

	private void writeValues(Sensor dt, TimelineStore tl, BufferedWriter writer) throws IOException {
		int lag = lag(dt);
		int timeHorizon = timeHorizon(dt);
		Timeline timeline = tl.timeline();
		for (Magnitude m : timeline.magnitudes())
			if (m.model.attribute("type").equals("Numeric"))
				timeline = timeline.add(m, timeline.get(m).normalize());
		timeline.stream().skip(lag).forEach(p -> writePoint(writer, p, timeHorizon, lag));
	}

	private void writePoint(BufferedWriter writer, Timeline.Point p, int timeHorizon, int lag) {
		var builder = new StringBuilder().append(dateTimeColumns(p.instant())).append(SEPARATOR).append(magnitudeColumns(p));
		if (timeHorizon > 0) {
			Timeline.Point pointOnHorizon = p.step(timeHorizon);
			if (pointOnHorizon == null) return;
			builder.append(SEPARATOR).append(magnitudeColumns(pointOnHorizon));
		}
		if (lag != 0) {
			String prefix = builder.toString();
			StringBuilder lagBuilder = new StringBuilder();
			IntStream.range(0, lag + 1).forEach(i -> {
				lagBuilder.append(prefix);
				IntStream.range(0, lag).forEach(j -> {
					if (j < lag - i) lagBuilder.append("0");
					else lagBuilder.append(magnitudeColumns(p.step(-(j + 1))));
					if (j < lag - 1) lagBuilder.append(SEPARATOR);
				});
				lagBuilder.append("\n");
			});
			write(writer, lagBuilder.toString());
		} else write(writer, builder + "\n");
	}


	private void write(BufferedWriter writer, String row) {
		try {
			writer.write(row);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private Stream<String> labelOf(Magnitude m) {
		return "Cyclic".equals(m.model().attribute("type")) ?
				cyclicLabels(m.label) :
				Stream.of(m.label);
	}

	private String dateTimeLabels() {
		return stream(ChronoFields)
				.map(f -> f.name().toLowerCase())
				.flatMap(DigitalTwinBuilder::cyclicLabels)
				.collect(joining(SEPARATOR));
	}

	private static Stream<String> cyclicLabels(String m) {
		return Stream.of("cos_" + m, "sin_" + m);
	}

	private void untar(InputStream trainer) {
		try {
			Tar.extractTarFile(trainer, this.sourcesDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private void clean() {
		try {
			FileUtils.deleteDirectory(this.sourcesDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	public static interface OnFinished {

		void onFinished(String report);
	}
}
