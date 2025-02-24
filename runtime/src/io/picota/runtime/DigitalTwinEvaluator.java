package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.TimelineStore;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Long.parseLong;
import static java.time.temporal.ChronoField.*;

public class DigitalTwinEvaluator {
	private final DataHubBox datahub;
	private final File sourcesDir;
	private final File pythonVenv;
	private final File modelsDir;
	private final ScheduledExecutorService executorService;
	private final List<Sensor> sensors;
	private final File dataDir;

	public DigitalTwinEvaluator(DataHubBox datahub, File workingDir, File pythonVenv) {
		this.datahub = datahub;
		this.modelsDir = new File(workingDir, "models");
		this.sourcesDir = new File(workingDir, "sources");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		if (this.sourcesDir.exists()) clean();
		this.sourcesDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
		this.sensors = datahub.graph().sensorList();
		this.executorService = Executors.newScheduledThreadPool(sensors.size());
	}

	public void start() {
		if (executorService.isShutdown()) throw new IllegalStateException("Executor is running");
		for (Sensor digitalTwin : sensors) {
			Logger.info("Starting DT " + digitalTwin + "...");
			executorService.scheduleAtFixedRate(() -> inferAndPublish(digitalTwin), 0,
					parseLong(attributeOf(digitalTwin, "resolution")),
					TimeUnit.valueOf(attributeOf(digitalTwin, "resolutionScale").toUpperCase()));
		}
		untar(this.getClass().getResourceAsStream("evaluator.tar"));
	}

	private void inferAndPublish(Sensor digitalTwin) {
		try {
			TimelineStore timelineStore = digitalTwin(digitalTwin.name$());
			Timeline.Point last = timelineStore.timeline().last();
			if (last == null) return;
			List<Sensor.Magnitude> inferences = inferentialVariables(digitalTwin);
			File dataFile = new File(dataDir, digitalTwin.name$() + ".csv");
			writeData(digitalTwin,dataFile, last, last.magnitudes().stream().filter(m -> inferences.stream().noneMatch(mg -> mg.name$().equalsIgnoreCase(m.label))));
			Map<Sensor.Magnitude, Double> inference = runInferences(inferences);
			publish(digitalTwin, inference);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	@NotNull
	private Map<Sensor.Magnitude, Double> runInferences(List<Sensor.Magnitude> inferences) {
		return inferences.parallelStream()
				.collect(Collectors.toMap(m -> m, m -> {
					try {
						return Double.parseDouble(infer(m).trim());
					} catch (IOException | InterruptedException e) {
						Logger.error(e);
						return Double.NaN;
					}
				}));
	}

	private void writeData(Sensor digitalTwin, File file, Timeline.Point last, Stream<Magnitude> magnitudeStream) throws IOException {
		Files.writeString(file.toPath(), magnitudeStream.map(m -> String.valueOf(last.value(m))).collect(Collectors.joining(",")));
	}

	private void publish(Sensor digitalTwin, Map<Sensor.Magnitude, Double> inference) {

	}

	private String infer(Sensor.Magnitude m) throws IOException, InterruptedException {
		Logger.info("Inferring digital twin magnitude...");
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(sourcesDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), modelsDir.getAbsolutePath())
				.directory(sourcesDir)
				.start();
		Logger.info("Finished evaluation of variable. Code: " + process.waitFor());
		return new String(process.getInputStream().readAllBytes()).trim();
	}

	private List<Sensor.Magnitude> inferentialVariables(Sensor digitalTwin) {
		return digitalTwin.magnitudeList().stream()
				.filter(m -> attributeOf(m, "inference") != null)
				.toList();
	}

	public void stop() {
		executorService.shutdownNow();
		clean();
	}

	private static String attributeOf(Sensor digitalTwin, String attr) {
		Sensor.Attribute attribute = digitalTwin.attribute(a -> a.name$().equalsIgnoreCase(attr));
		return attribute != null ? attribute.value() : null;
	}

	private static String attributeOf(Sensor.Magnitude magnitude, String attr) {
		Sensor.Magnitude.Attribute attribute = magnitude.attribute(a -> a.name$().equalsIgnoreCase(attr));
		return attribute != null ? attribute.value() : null;
	}



	private TimelineStore digitalTwin(String name) {
		return datahub.datamarts().get("master").timelineStore().get(name, name);
	}

	private static final ChronoField[] chronoFields = new ChronoField[]{HOUR_OF_DAY, DAY_OF_WEEK, DAY_OF_MONTH, MONTH_OF_YEAR};

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
}
