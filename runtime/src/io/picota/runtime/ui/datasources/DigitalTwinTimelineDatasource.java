package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;
import io.intino.alexandria.ui.services.push.UISession;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.TimeSeries;
import io.intino.sumus.chronos.TimelineStore;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static java.time.ZoneOffset.UTC;

public class DigitalTwinTimelineDatasource implements TimelineDatasource {
	private final RuntimeBox box;
	private final UISession session;
	private final DigitalTwin digitalTwin;
	private final Sensor sensor;

	public DigitalTwinTimelineDatasource(RuntimeBox box, UISession session, DigitalTwin digitalTwin) {
		this.box = box;
		this.session = session;
		sensor = box.datahub().graph().sensorList(s -> s.name$().equalsIgnoreCase(digitalTwin.title())).findFirst().get();
		this.digitalTwin = digitalTwin;
	}

	@Override
	public String name() {
		return digitalTwin.title();
	}

	@Override
	public List<MagnitudeDefinition> magnitudes() {
		return sensor.magnitudeList().stream().map(m -> magnitudeOf(m.name$(), "", m.name$())).toList();
	}

	@Override
	public Magnitude magnitude(MagnitudeDefinition definition) {
		return measurements(definition, timeline());
	}

	private TimelineStore timeline() {
		return box.datahub().datamarts().get("master").timelineStore().get(digitalTwin.title, digitalTwin.title);
	}

	@Override
	public List<Scale> scales() {
//		String value = sensor.attribute(a -> a.name$().equalsIgnoreCase("resolutionScale")).value();
//		Scale scale = Scale.valueOf(value);
//		return stream(Scale.values(), 0, scale.ordinal() + 1).toList();
		return List.of(Scale.Hour, Scale.Day);
	}

	@Override
	public Instant from(Scale scale) {
		return LocalDateTime.ofInstant(Instant.now(), UTC).minus(30, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant to(Scale scale) {
		return Instant.now();
	}

	/*
	@Override
	public Instant from(Scale scale) {
		try {
			return timeline().timeline().first().instant();
		} catch (IOException e) {
			Logger.error(e);
			return Instant.EPOCH;
		}
	}

	@Override
	public Instant to(Scale scale) {
		try {
			return timeline().timeline().last().instant();
		} catch (IOException e) {
			Logger.error(e);
			return Instant.now();
		}
	}
*/
	private MagnitudeDefinition magnitudeOf(String name, String unit, String label) {
		return new MagnitudeDefinition()
				.name(name)
				.unit(unit).add("en", label)
				.formatter(value -> String.format(Locale.ENGLISH, "%.2f", value));
	}

	private TimelineDatasource.Magnitude measurements(MagnitudeDefinition magnitude, TimelineStore store) {
		try {
			return store == null || store.timeline() == null ?
					new NullMagnitude(magnitude) :
					getMagnitude(magnitude, store.timeline().get(magnitude.name()));
		} catch (IOException e) {
			Logger.error(e);
			return null;
		}
	}

	@NotNull
	private Magnitude getMagnitude(MagnitudeDefinition magnitude, TimeSeries series) {
		return series == null ? new NullMagnitude(magnitude) : new TimeSeriesMagnitude(series, magnitude);
	}
}
