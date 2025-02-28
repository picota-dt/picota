package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.datenavigator.DateNavigatorDatasource;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Period;
import io.intino.sumus.chronos.Timeline;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.stream;

public class DigitalTwinDateNavigatorDatasource implements DateNavigatorDatasource {
	private final Sensor sensor;
	private final Timeline timeline;

	public DigitalTwinDateNavigatorDatasource(Sensor sensor, Timeline timeline) {
		this.sensor = sensor;
		this.timeline = timeline;
	}

	@Override
	public List<Scale> scales() {
		String value = sensor.attribute(a -> a.name$().equalsIgnoreCase("resolutionScale")).value();
		Scale scale = Scale.valueOf(value);
		return stream(Scale.values(), 0, scale.ordinal() + 1).toList();
	}

	@Override
	public Instant from(Scale scale) {
		return timeline == null ? Instant.EPOCH : rescaled(timeline, scale).first().instant();
	}

	@Override
	public Instant to(Scale scale) {
		return timeline == null ? Instant.now() : rescaled(timeline, scale).last().instant();
	}

	@Override
	public Instant previous(Scale scale, Instant date) {
		return LocalDateTime.ofInstant(date, UTC).minus(1, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant next(Scale scale, Instant date) {
		return LocalDateTime.ofInstant(date, UTC).plus(1, scale.temporalUnit()).toInstant(UTC);
	}

	private Timeline rescaled(Timeline timeline, Scale scale) {
		return timeline.resampleBy(Period.each(1, (ChronoUnit) scale.temporalUnit()));
	}
}
