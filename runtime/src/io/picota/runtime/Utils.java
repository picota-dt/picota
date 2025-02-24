package io.picota.runtime;

import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Magnitude;
import io.intino.sumus.chronos.Timeline;
import io.picota.runtime.rest.resources.GetStatusResource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.time.temporal.ChronoField.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class Utils {
	public static final String SEPARATOR = ",";
	public static final ChronoField[] ChronoFields = new ChronoField[]{HOUR_OF_DAY, DAY_OF_WEEK, DAY_OF_MONTH, MONTH_OF_YEAR};

	public static GetStatusResource.Moment inferenceMoment(Sensor digitalTwin) {
		return GetStatusResource.Moment.valueOf(attributeOf(digitalTwin, "inference"));
	}

	public static int lag(Sensor digitalTwin) {
		String lag = attributeOf(digitalTwin, "lag");
		return lag == null ? 0 : Integer.parseInt(lag);
	}

	public static int timeHorizon(Sensor digitalTwin) {
		String timeHorizon = attributeOf(digitalTwin, "timeHorizon");
		return timeHorizon == null ? 0 : Integer.parseInt(timeHorizon);
	}

	public static String attributeOf(Sensor digitalTwin, String attr) {
		Sensor.Attribute attribute = digitalTwin.attribute(a -> a.name$().equalsIgnoreCase(attr));
		return attribute != null ? attribute.value() : null;
	}

	public static String magnitudeColumns(Timeline.Point p) {
		return p.magnitudes().stream()
				.flatMap(m -> normalize(p.value(m), m))
				.map(Object::toString)
				.collect(joining(SEPARATOR));
	}

	public static String dateTimeColumns(Instant instant) {
		ZonedDateTime dateTime = instant.atZone(ZoneId.of("UTC"));
		return stream(ChronoFields)
				.flatMap(f -> cyclicValues(dateTime.get(f), f.range().getMaximum()))
				.map(Objects::toString)
				.collect(joining(SEPARATOR));
	}

	private static Stream<Double> normalize(double v, Magnitude magnitude) {
		return "Cyclic".equals(magnitude.model().attribute("type")) ? cyclicValues(v, magnitude.max()) : Stream.of(v);
	}

	private static Stream<Double> cyclicValues(double v, double max) {
		return Stream.of(Math.cos(Math.cos(2 * Math.PI * v / max)), Math.sin(Math.cos(2 * Math.PI * v / max)));
	}
}
