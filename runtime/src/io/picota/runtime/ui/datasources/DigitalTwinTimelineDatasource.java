package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Timeline;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.picota.runtime.ui.datasources.TimeSeriesMagnitude.periodOf;
import static java.util.Arrays.stream;

public class DigitalTwinTimelineDatasource implements TimelineDatasource {
	private final Sensor sensor;
	private final Timeline timeline;
	private final Map<Sensor.Magnitude, Double> inferences;

	public DigitalTwinTimelineDatasource(Sensor sensor, Timeline timeline, Map<Sensor.Magnitude, Double> inferences) {
		this.sensor = sensor;
		this.timeline = timeline;
		this.inferences = inferences;
	}

	@Override
	public String name() {
		return sensor.name$();
	}

	@Override
	public List<MagnitudeDefinition> magnitudes() {
		return sensor.magnitudeList().stream().map(m -> magnitudeOf(m.name$(), "", m.name$())).toList();
	}

	@Override
	public Magnitude magnitude(MagnitudeDefinition definition) {
		return measurements(definition);
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
		if (timeline == null) return Instant.now();
		return rescaled(timeline, scale).last().instant();
	}

	private Timeline rescaled(Timeline timeline, Scale scale) {
		return timeline.resampleBy(periodOf(scale));
	}

	private MagnitudeDefinition magnitudeOf(String name, String unit, String label) {
		return new MagnitudeDefinition()
				.name(name)
				.unit(unit).add("en", label)
				.formatter(value -> String.format(Locale.ENGLISH, "%.2f", value));
	}

	private TimelineDatasource.Magnitude measurements(MagnitudeDefinition magnitude) {
		return timeline == null ?
				new NullMagnitude(magnitude) :
				getMagnitude(magnitude, timeline);
	}

	@NotNull
	private Magnitude getMagnitude(MagnitudeDefinition magnitude, Timeline timeline) {
		return timeline.get(magnitude.name()) == null ?
				new NullMagnitude(magnitude) :
				new TimeSeriesMagnitude(timeline, magnitude, inferences.get(sensor.magnitude(m->m.name$().equalsIgnoreCase(magnitude.name()))));
	}
}
