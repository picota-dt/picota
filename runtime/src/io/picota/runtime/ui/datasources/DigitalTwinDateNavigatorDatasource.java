package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.intino.alexandria.ui.model.datenavigator.DateNavigatorDatasource;
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
import java.util.Locale;

import static java.time.ZoneOffset.UTC;

public class DigitalTwinDateNavigatorDatasource implements DateNavigatorDatasource {
	private final RuntimeBox box;
	private final UISession session;
	private final DigitalTwin digitalTwin;
	private final Sensor sensor;

	public DigitalTwinDateNavigatorDatasource(RuntimeBox box, UISession session, DigitalTwin digitalTwin) {
		this.box = box;
		this.session = session;
		sensor = box.datahub().graph().sensorList(s -> s.name$().equalsIgnoreCase(digitalTwin.title())).findFirst().get();
		this.digitalTwin = digitalTwin;
	}

	@Override
	public List<Scale> scales() {
		return List.of(Scale.Hour, Scale.Day);
	}

	@Override
	public Instant from(Scale scale) {
		return LocalDateTime.ofInstant(Instant.now(), UTC).minus(30, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant previous(Scale scale, Instant date) {
		return LocalDateTime.ofInstant(date, UTC).minus(1, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant next(Scale scale, Instant date) {
		return LocalDateTime.ofInstant(date, UTC).plus(1, scale.temporalUnit()).toInstant(UTC);
	}

	@Override
	public Instant to(Scale scale) {
		return Instant.now();
	}

}
