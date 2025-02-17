package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.magritte.framework.Layer;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.TimelineStore;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static java.time.temporal.ChronoField.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class DigitalTwinBuilder {
	public static final String SEPARATOR = ",";
	private final DataHubBox box;
	private final File sourcesDir;
	private final File pythonVenv;
	private final File scripts;
	private final File modelsDir;
	private final File dataDir;
	private final ExecutorService executor;
	private Future<?> current;

	public DigitalTwinBuilder(DataHubBox box, File workingDir, File pythonVenv, File scripts) {
		this.box = box;
		this.modelsDir = new File(workingDir, "models");
		this.sourcesDir = new File(workingDir, "sources");
		this.dataDir = new File(workingDir, "data");
		this.pythonVenv = pythonVenv;
		this.scripts = scripts;
		if (this.sourcesDir.exists()) clean();
		this.sourcesDir.mkdirs();
		this.modelsDir.mkdirs();
		this.dataDir.mkdirs();
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void start() {
		if (current != null && !current.isDone()) throw new IllegalStateException("Executor is not terminated");
		current = this.executor.submit(() -> {
			try {
				for (String digitalTwin : box.graph().sensorList().stream().map(Layer::name$).toList()) {
					Logger.info("Preparing data for " + digitalTwin + "...");
					prepareData(digitalTwin(digitalTwin));
				}
				FileUtils.copyDirectory(scripts, sourcesDir);
				train();
				clean();
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
			}
		});
	}

	public void stop() {
		if (current != null) {
			current.cancel(true);
			clean();
		}
	}

	private void train() throws IOException, InterruptedException {
		Logger.info("Training digital twins...");
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(sourcesDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		Process process = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), dataDir.getAbsolutePath(), modelsDir.getAbsolutePath())
				.directory(sourcesDir)
				.start();
		Logger.info("Finished training of digital twins. Code: " + process.waitFor());
		String report = new String(process.getInputStream().readAllBytes());
		System.out.println(report);
	}

	private TimelineStore digitalTwin(String name) {
		return box.datamarts().get("master").timelineStore().get(name, name);
	}

	private void prepareData(TimelineStore tl) throws IOException {
		if (tl == null) return;
		File file = new File(dataDir, tl.id() + ".csv");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write(header(tl));
			Timeline timeline = tl.timeline();
			for (Magnitude m : timeline.magnitudes())
				if (m.model.attribute("type").equals("Numeric"))
					timeline = timeline.add(m, timeline.get(m).normalize());
			for (Timeline.Point p : timeline) writer.write(mapPoint(p));
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private String header(TimelineStore tl) {
		String datetimeLabels = dateTimeLabels();
		String magnitudes = stream(tl.sensorModel().magnitudes()).flatMap(this::labelOf).collect(joining(SEPARATOR));
		return datetimeLabels + "," + magnitudes + "\n";
	}

	private Stream<String> labelOf(Magnitude m) {
		return "Cyclic".equals(m.model().attribute("type")) ?
				cyclicLabels(m.label) :
				Stream.of(m.label);
	}

	private static final ChronoField[] chronoFields = new ChronoField[]{HOUR_OF_DAY, DAY_OF_WEEK, DAY_OF_MONTH, MONTH_OF_YEAR};

	private String dateTimeLabels() {
		return stream(chronoFields)
				.map(f -> f.name().toLowerCase())
				.flatMap(DigitalTwinBuilder::cyclicLabels)
				.collect(joining(SEPARATOR));
	}

	private static Stream<String> cyclicLabels(String m) {
		return Stream.of("cos_" + m, "sin_" + m);
	}

	private String mapPoint(Timeline.Point p) {
		String magnitudeColumns = p.magnitudes().stream()
				.flatMap(m -> normalize(p.value(m), m))
				.map(Object::toString)
				.collect(joining(SEPARATOR));
		return dateTimeColumns(p.instant()) + SEPARATOR + magnitudeColumns + "\n";
	}

	private String dateTimeColumns(Instant instant) {
		ZonedDateTime dateTime = instant.atZone(ZoneId.of("UTC"));
		return stream(chronoFields).flatMap(f -> cyclicValues(dateTime.get(f), f.range().getMaximum())).map(Objects::toString).collect(joining(SEPARATOR));
	}

	private Stream<Double> normalize(double v, Magnitude magnitude) {
		return "Cyclic".equals(magnitude.model().attribute("type")) ? cyclicValues(v, magnitude.max()) : Stream.of(v);
	}

	private static Stream<Double> cyclicValues(double v, double max) {
		return Stream.of(Math.cos(Math.cos(2 * Math.PI * v / max)), Math.sin(Math.cos(2 * Math.PI * v / max)));
	}

	private void clean() {
		try {
			FileUtils.deleteDirectory(this.sourcesDir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}
}
