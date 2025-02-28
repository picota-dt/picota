package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource.Serie;
import io.intino.sumus.chronos.Period;
import io.intino.sumus.chronos.TimeSeries;
import io.intino.sumus.chronos.TimeSeries.Point;
import io.intino.sumus.chronos.Timeline;
import io.intino.sumus.chronos.models.descriptive.timeseries.Distribution;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.time.ZoneId.systemDefault;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.StreamSupport.stream;

class TimeSeriesMagnitude implements TimelineDatasource.Magnitude {
	private final Timeline timeline;
	private final TimeSeries series;
	private final MagnitudeDefinition magnitude;

	public TimeSeriesMagnitude(Timeline timeline, MagnitudeDefinition magnitude) {
		this.timeline = timeline;
		this.series = timeline.get(magnitude.name());
		this.magnitude = magnitude;
	}

	@Override
	public TimelineDatasource.Summary summary(Scale scale, Instant instant) {
		return summary(rescaled(scale));
	}

	@Override
	public Serie serie(Scale scale, Instant start, Instant end) {
		Instant inclusiveEnd;
		if (scale.equals(Scale.Month)) inclusiveEnd = addMonth(end);
		else if (scale.equals(Scale.Week)) inclusiveEnd = addWeek(end);
		else inclusiveEnd = end.plus(1, scale.temporalUnit());
		return serie(stream(spliteratorUnknownSize(rescaled(scale).from(start, inclusiveEnd).iterator(), ORDERED), false).toList());
	}

	private Instant addMonth(Instant end) {
		return end.atZone(systemDefault()).plusMonths(1).toInstant();
	}

	private Instant addWeek(Instant end) {
		return end.atZone(systemDefault()).plusWeeks(1).toInstant();
	}

	@Override
	public Serie serie(Scale scale, Instant end, int count) {
		Point at = rescaled(scale).at(end);
		if (at == null) return null;
		return serie(at.backward().limit(count).toList().reversed());
	}

	private TimeSeries rescaled(Scale scale) {
		return timeline.resampleBy(periodOf(scale)).get(magnitude.name());
	}

	public static Period periodOf(Scale scale) {
		if (scale == Scale.Minute) return Period.Minutes;
		if (scale == Scale.Hour) return Period.Hours;
		if (scale == Scale.Day) return Period.Days;
		if (scale == Scale.Week) return Period.Weeks;
		if (scale == Scale.Month) return Period.Months;
		if (scale == Scale.Year) return Period.Years;
		return Period.Hours;
	}

	@NotNull
	private Serie serie(List<Point> points) {
		var values = points.stream().collect(toMap(Point::instant, Point::value, (v1, v2) -> v1, LinkedHashMap::new));
		return new Serie() {
			@Override
			public String name() {
				return "Evolution";
			}

			@Override
			public Map<Instant, Double> values() {
				return values;
			}

			@Override
			public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
				return Map.of();
			}
		};
	}

	@Override
	public MagnitudeDefinition definition() {
		return magnitude;
	}

	@Override
	public Status status() {
		return null;
	}

	@Override
	public double value() {
		return series.last().value();
	}

	@Override
	public Double min() {
		return Arrays.stream(series.values).min().getAsDouble();
	}

	@Override
	public Double max() {
		return Arrays.stream(series.values).max().getAsDouble();
	}

	@Override
	public Double percentage() {
		return null;
	}

	@NotNull
	private TimelineDatasource.Summary summary(TimeSeries timeSeries) {
		Distribution distribution = timeSeries.distribution();
		return new TimelineDatasource.Summary() {

			@Override
			public double average() {
				return distribution.mean();
			}

			@Override
			public Instant averageDate() {
				return null;
			}

			@Override
			public double max() {
				return distribution.max;
			}

			@Override
			public Instant maxDate() {
				for (Point point : timeSeries) if (point.value() == max()) return point.instant();
				return null;
			}

			@Override
			public double min() {
				return distribution.min;
			}

			@Override
			public Instant minDate() {
				for (Point point : timeSeries) if (point.value() == min()) return point.instant();
				return null;
			}

			@Override
			public List<Attribute> attributes() {
				return List.of();
			}
		};
	}

	@Override
	public String customHtmlView(Scale scale) {
		return "";
	}

}
