package io.picota.runtime;

import io.intino.alexandria.logger.Logger;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.TimelineStore;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.picota.runtime.Utils.*;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class CsvUtils {

	static String headerForBuild(Sensor dt, TimelineStore tl) {
		StringBuilder header = new StringBuilder();
		header.append(dateTimeLabels());
		header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
				.flatMap(CsvUtils::labelOf)
				.collect(joining(SEPARATOR)));
		int timeHorizon = timeHorizon(dt);
		if (timeHorizon > 0) appendTimeHorizonLabels(tl, header, timeHorizon);
		appendLagLabels(dt, tl, header);
		return header + "\n";
	}

	static String headerForInfer(Sensor dt, TimelineStore tl) {
		StringBuilder header = new StringBuilder();
		header.append(dateTimeLabels());
		header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
				.flatMap(CsvUtils::labelOf)
				.collect(joining(SEPARATOR)));
		appendLagLabels(dt, tl, header);
		return header + "\n";
	}

	public static void writeValuesForTrain(Sensor dt, TimelineStore tl, BufferedWriter writer) throws IOException {
		int lag = lag(dt);
		int timeHorizon = timeHorizon(dt);
		normalized(tl.timeline()).stream().skip(lag).forEach(p -> writePoint(writer, p, timeHorizon, lag));
	}

	public static void writeValuesForInfer(Sensor dt, TimelineStore tl, BufferedWriter writer) throws IOException {
		writePoint(writer, normalized(tl.timeline()).last(), 0, lag(dt));
	}

	private static Timeline normalized(Timeline timeline) {
		for (Magnitude m : timeline.magnitudes())
			if (m.model.attribute("type").equals("Numeric"))
				timeline = timeline.add(m, timeline.get(m).normalize());
		return timeline;
	}

	public static void writePoint(BufferedWriter writer, Timeline.Point p, int timeHorizon, int lag) {
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


	private static void write(BufferedWriter writer, String row) {
		try {
			writer.write(row);
		} catch (IOException e) {
			Logger.error(e);
		}
	}


	private static void appendLagLabels(Sensor dt, TimelineStore tl, StringBuilder header) {
		IntStream.range(0, lag(dt)).forEach(i ->
				header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
						.flatMap(CsvUtils::labelOf)
						.map(label -> format("%s-%d", label, i))
						.collect(joining(SEPARATOR))));
	}

	private static void appendTimeHorizonLabels(TimelineStore tl, StringBuilder header, int timeHorizon) {
		header.append(SEPARATOR).append(stream(tl.sensorModel().magnitudes())
				.flatMap(CsvUtils::labelOf)
				.map(label -> format("%s+%d", label, timeHorizon))
				.collect(joining(SEPARATOR)));
	}

	private static Stream<String> labelOf(Magnitude m) {
		return "Cyclic".equals(m.model().attribute("type")) ?
				cyclicLabels(m.label) :
				Stream.of(m.label);
	}

	private static String dateTimeLabels() {
		return stream(ChronoFields)
				.map(f -> f.name().toLowerCase())
				.flatMap(CsvUtils::cyclicLabels)
				.collect(joining(SEPARATOR));
	}

	public static void untar(InputStream trainer, File dir) {
		try {
			Tar.extractTarFile(trainer, dir);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static Stream<String> cyclicLabels(String m) {
		return Stream.of("cos_" + m, "sin_" + m);
	}

}
