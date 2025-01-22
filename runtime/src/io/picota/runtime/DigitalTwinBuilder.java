package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.box.DataHubBox;
import io.intino.datahub.datamart.MasterDatamart;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.TimelineStore;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoField.*;
import static java.util.Arrays.stream;

public class DigitalTwinBuilder {
	public static final String SEPARATOR = ",";
	private final DataHubBox box;
	private final File workingDir;
	private final File pythonVenv;
	private final InputStream scripts;

	public DigitalTwinBuilder(DataHubBox box, File workingDir, File pythonVenv, InputStream scripts) {
		this.box = box;
		this.workingDir = new File(workingDir, "sources");
		this.pythonVenv = pythonVenv;
		this.scripts = scripts;
		workingDir.mkdirs();
	}

	public void build() {
		MasterDatamart master = box.datamarts().get("master");
		master.timelineStore().stream().forEach(tl -> {
			try {
				File csv = transformToCsv(tl);
				Tar.extractTarFile(scripts, workingDir);
				runTraining(csv);
			} catch (IOException | InterruptedException e) {
				Logger.error(e);
			}
		});
	}

	private void runTraining(File csv) throws IOException, InterruptedException {
		String pythonExecutable = pythonVenv.getAbsolutePath() + "/bin/python";
		File scriptPath = new File(workingDir, "main.py");
		if (!scriptPath.exists()) throw new IOException("Main script not found: " + scriptPath.getAbsolutePath());
		ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptPath.getAbsolutePath(), csv.getAbsolutePath());
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		Process process = pb.start();
		System.out.println("Exit code: " + process.waitFor());
	}

	private File transformToCsv(TimelineStore tl) throws IOException {
		File file = new File(workingDir, tl.id() + ".csv");
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
			writer.write(header(tl));
			for (Timeline.Point p : tl.timeline()) writer.write(mapRow(p));
		} catch (IOException e) {
			Logger.error(e);
		}
		return file;
	}

	private String header(TimelineStore tl) {
		String datetimeLabels = dateTimeLabels();
		String magnitudes = stream(tl.sensorModel().magnitudes()).flatMap(this::labelOf).collect(Collectors.joining(SEPARATOR));
		return datetimeLabels + "," + magnitudes;
	}

	private Stream<String> labelOf(Magnitude m) {
		return "Cyclic".equals(m.model().attribute("type")) ?
				Stream.of("cos_" + m.label, "sin_" + m.label) :
				Stream.of(m.label);
	}

	private static final ChronoField[] chronoFields = new ChronoField[]{HOUR_OF_DAY, DAY_OF_WEEK, DAY_OF_MONTH, MONTH_OF_YEAR};

	private String dateTimeLabels() {
		return stream(chronoFields)
				.map(f -> f.name().toLowerCase())
				.collect(Collectors.joining(SEPARATOR));
	}

	private String mapRow(Timeline.Point p) {
		String magnitudeColumns = p.magnitudes().stream()
				.flatMap(m -> normalize(p.value(m), m))
				.map(Object::toString)
				.collect(Collectors.joining(SEPARATOR));
		return dateTimeColumns(p.instant()) + SEPARATOR + magnitudeColumns;

	}

	private String dateTimeColumns(Instant instant) {
		ZonedDateTime dateTime = instant.atZone(ZoneId.of("UTC"));
		return stream(chronoFields).map(dateTime::get).map(Object::toString).collect(Collectors.joining(SEPARATOR));
	}

	private Stream<Double> normalize(double v, Magnitude magnitude) {
		String type = magnitude.model().attribute("type");
		if ("Cyclic".equals(type))
			return Stream.of(Math.cos(Math.cos(2 * Math.PI * v / magnitude.max())), Math.sin(Math.cos(2 * Math.PI * v / magnitude.max())));
		else if ("Numeric".equals(type)) {
			double max = magnitude.max();
			double min = magnitude.min();
			return Stream.of((v - min) / (max - min));
		}
		return Stream.of(v);
	}

}
