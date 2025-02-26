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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
		return sensor.magnitudeList().stream().map(m -> measurementOf(m.name$(), "", m.name$())).toList();
	}

	@Override
	public Magnitude magnitude(MagnitudeDefinition definition) {
		return measurements(definition, box.datahub().datamarts().get("master").timelineStore().get(definition.name(), definition.name()));
	}

	@Override
	public List<Scale> scales() {
		return List.of(Scale.Minute, Scale.Hour, Scale.Day, Scale.Week, Scale.Month, Scale.Year);
	}

	@Override
	public Instant from(Scale scale) {
		return LocalDateTime.ofInstant(Instant.now(), UTC).minus(30, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant to(Scale scale) {
		return Instant.now();
	}

	private MagnitudeDefinition measurementOf(String name, String unit, String label) {
		return new MagnitudeDefinition().name(name).unit(unit).add("es", label);
	}

	private String customViewOf(String title, Map<String, Integer> variables) {
		return "<h4 style=\"padding:'0';margin:'0'\">" + title + "</h4>" + variables.entrySet().stream().map(v -> "<div style=\"fontWeight:'bold'\">" + v.getKey() + "</div><div>" + v.getValue() + "</div>").collect(Collectors.joining(""));
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
