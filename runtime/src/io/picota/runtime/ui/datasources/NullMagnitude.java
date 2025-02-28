package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NullMagnitude(MagnitudeDefinition definition) implements TimelineDatasource.Magnitude {

	@Override
	public Status status() {
		return null;
	}

	@Override
	public double value() {
		return 0;
	}

	@Override
	public Double min() {
		return 0.0;
	}

	@Override
	public Double max() {
		return 0.0;
	}

	@Override
	public Double percentage() {
		return 0.0;
	}

	@Override
	public TimelineDatasource.Summary summary(Scale scale, Instant instant) {
		return new TimelineDatasource.Summary() {
			@Override
			public double average() {
				return 0;
			}

			@Override
			public Instant averageDate() {
				return null;
			}

			@Override
			public double max() {
				return 0;
			}

			@Override
			public Instant maxDate() {
				return null;
			}

			@Override
			public double min() {
				return 0;
			}

			@Override
			public Instant minDate() {
				return null;
			}

			@Override
			public List<Attribute> attributes() {
				return List.of();
			}
		};
	}

	@Override
	public TimelineDatasource.Serie serie(Scale scale, Instant end, int pointsCount) {
		return new TimelineDatasource.Serie() {
			@Override
			public String name() {
				return "";
			}

			@Override
			public Map<Instant, Double> values() {
				return Map.of();
			}

			@Override
			public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
				return Map.of();
			}
		};
	}

	@Override
	public TimelineDatasource.Serie serie(Scale scale, Instant start, Instant end) {
		return new TimelineDatasource.Serie() {
			@Override
			public String name() {
				return "";
			}

			@Override
			public Map<Instant, Double> values() {
				return Map.of();
			}

			@Override
			public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
				return Map.of();
			}
		};
	}

	@Override
	public String customHtmlView(Scale scale) {
		return "";
	}
}
