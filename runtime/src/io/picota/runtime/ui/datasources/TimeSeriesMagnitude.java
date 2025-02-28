package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;
import io.intino.sumus.chronos.TimeSeries;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

class TimeSeriesMagnitude implements TimelineDatasource.Magnitude {
	private final TimeSeries series;
	private final MagnitudeDefinition magnitude;

	public TimeSeriesMagnitude(TimeSeries series, MagnitudeDefinition magnitude) {
		this.series = series;
		this.magnitude = magnitude;
	}

	@Override
	public TimelineDatasource.Summary summary(Instant first, Scale scale) {
		return summary(series.at(first).forward().limit(24).toList());
	}

	@Override
	public TimelineDatasource.Serie serie(Scale scale, Instant start, Instant end) {
		return serie(series.from(start, end).first().forward().toList());
	}

	@Override
	public TimelineDatasource.Serie serie(Scale scale, Instant first, int count) {
		return serie(series.at(first).forward().limit(count).toList());
	}

	@NotNull
	private TimelineDatasource.Serie serie(List<TimeSeries.Point> list) {
		return new TimelineDatasource.Serie() {
			@Override
			public String name() {
				return "Evolution";
			}

			@Override
			public Map<Instant, Double> values() {
				return list.stream().collect(Collectors.toMap(TimeSeries.Point::instant, TimeSeries.Point::value));
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
	private TimelineDatasource.Summary summary(List<TimeSeries.Point> list) {
		return new TimelineDatasource.Summary() {

			@Override
			public double average() {
				return values(list).average().getAsDouble();
			}

			@Override
			public Instant averageDate() {
				double average = average();
				return list.stream().filter(p -> p.value() == average).findFirst().get().instant();
			}

			@Override
			public double max() {
				return values(list).max().getAsDouble();
			}

			@Override
			public Instant maxDate() {
				return list.getLast().instant();
			}

			@Override
			public double min() {
				return values(list).min().getAsDouble();
			}

			@Override
			public Instant minDate() {
				return list.getFirst().instant();
			}

			@NotNull
			private DoubleStream values(List<TimeSeries.Point> list) {
				return list.stream().mapToDouble(TimeSeries.Point::value);
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
