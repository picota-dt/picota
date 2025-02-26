package io.picota.runtime.ui.datasources;

import io.intino.alexandria.Scale;
import io.intino.alexandria.ui.model.timeline.MagnitudeDefinition;
import io.intino.alexandria.ui.model.timeline.TimelineDatasource;
import io.intino.alexandria.ui.services.push.UISession;
import io.picota.runtime.RuntimeBox;
import io.picota.runtime.ui.DigitalTwin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

public class DigitalTwinTimelineDatasource implements TimelineDatasource {
	private final RuntimeBox box;
	private final UISession session;
	private final DigitalTwin digitalTwin;

	public DigitalTwinTimelineDatasource(RuntimeBox box, UISession session, DigitalTwin digitalTwin) {
		this.box = box;
		this.session = session;
		this.digitalTwin = digitalTwin;
	}

	@Override
	public String name() {
		return digitalTwin.title();
	}

	@Override
	public List<MagnitudeDefinition> magnitudes() {
		// TODO devolver la definición de las variables que quieras visualizar
		return List.of(measurementOf("m1", "", "Medida 1"), measurementOf("m2", "", "Medida 2"));
	}

	@Override
	public Magnitude magnitude(MagnitudeDefinition definition) {
		// TODO devolver la variable que quieras visualizar
		if (definition.name().equalsIgnoreCase("m1")) return m1(definition);
		return m2(definition);
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

	private TimelineDatasource.Magnitude m1(MagnitudeDefinition definition) {
		return new TimelineDatasource.Magnitude() {

			@Override
			public TimelineDatasource.Summary summary(Instant date, Scale scale) {
				return new TimelineDatasource.Summary() {
					@Override
					public double average() {
						return 10;
					}

					@Override
					public Instant averageDate() {
						return Instant.now();
					}

					@Override
					public double max() {
						return 100;
					}

					@Override
					public Instant maxDate() {
						return Instant.now();
					}

					@Override
					public double min() {
						return 2;
					}

					@Override
					public Instant minDate() {
						return Instant.now();
					}
				};
			}

			@Override
			public MagnitudeDefinition definition() {
				return definition;
			}

			@Override
			public Status status() {
				return null;
			}

			@Override
			public double value() {
				return 11.0;
			}

			@Override
			public Double min() {
				return null;
			}

			@Override
			public Double max() {
				return null;
			}

			@Override
			public Double percentage() {
				return null;
			}

			@Override
			public TimelineDatasource.Serie serie(Scale scale, Instant instant) {
				LocalDateTime date = LocalDateTime.ofInstant(instant, UTC);
				return new TimelineDatasource.Serie() {
					@Override
					public String name() {
						return "Evolución";
					}

					@Override
					public Map<Instant, Double> values() {
						return new LinkedHashMap<>() {{
							put(date.minus(8, scale.temporalUnit()).toInstant(UTC), 120.0);
							put(date.minus(7, scale.temporalUnit()).toInstant(UTC), 100.0);
							put(date.minus(6, scale.temporalUnit()).toInstant(UTC), 10.0);
							put(date.minus(5, scale.temporalUnit()).toInstant(UTC), 20.0);
							put(date.minus(4, scale.temporalUnit()).toInstant(UTC), 1220.0);
							put(date.minus(3, scale.temporalUnit()).toInstant(UTC), 192.0);
							put(date.minus(2, scale.temporalUnit()).toInstant(UTC), 1232.0);
							put(date.minus(1, scale.temporalUnit()).toInstant(UTC), 12.0);
							put(date.toInstant(UTC), 12.0);
						}};
					}

					@Override
					public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
						return new LinkedHashMap<>() {{
							put(date.minus(5, scale.temporalUnit()).toInstant(UTC), List.of(annotationOf("Warning value")));
							put(date.minus(4, scale.temporalUnit()).toInstant(UTC), List.of(annotationOf("Out of range", "red"), annotationOf("Other error", "red")));
							put(date.minus(1, scale.temporalUnit()).toInstant(UTC), List.of(annotationOf("Value is not valid", "green")));
						}

							private TimelineDatasource.Annotation annotationOf(String label) {
								return new TimelineDatasource.Annotation(label);
							}

							private TimelineDatasource.Annotation annotationOf(String label, String color) {
								return new TimelineDatasource.Annotation(label, color);
							}
						};
					}
				};
			}

			@Override
			public TimelineDatasource.Serie serie(Scale scale, Instant start, Instant end) {
				return serie(scale, end);
			}

			@Override
			public String customHtmlView(Scale scale) {
				return customViewOf("Título", Map.of("Valor 1", 100));
			}

		};
	}

	private TimelineDatasource.Magnitude m2(MagnitudeDefinition definition) {
		return new TimelineDatasource.Magnitude() {

			@Override
			public TimelineDatasource.Summary summary(Instant date, Scale scale) {
				return new TimelineDatasource.Summary() {
					@Override
					public double average() {
						return 35;
					}

					@Override
					public Instant averageDate() {
						return Instant.now();
					}

					@Override
					public double max() {
						return 92;
					}

					@Override
					public Instant maxDate() {
						return Instant.now();
					}

					@Override
					public double min() {
						return 10;
					}

					@Override
					public Instant minDate() {
						return Instant.now();
					}
				};
			}

			@Override
			public MagnitudeDefinition definition() {
				return definition;
			}

			@Override
			public Status status() {
				return null;
			}

			@Override
			public double value() {
				return 3300.0;
			}

			@Override
			public Double min() {
				return null;
			}

			@Override
			public Double max() {
				return null;
			}

			@Override
			public Double percentage() {
				return null;
			}

			@Override
			public TimelineDatasource.Serie serie(Scale scale, Instant date) {
				return new TimelineDatasource.Serie() {
					@Override
					public String name() {
						return "Evolución";
					}

					@Override
					public Map<Instant, Double> values() {
						return Collections.emptyMap();
					}

					@Override
					public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
						return Collections.emptyMap();
					}
				};
			}

			@Override
			public TimelineDatasource.Serie serie(Scale scale, Instant start, Instant end) {
				return new TimelineDatasource.Serie() {
					@Override
					public String name() {
						return "Evolución";
					}

					@Override
					public Map<Instant, Double> values() {
						return Collections.emptyMap();
					}

					@Override
					public Map<Instant, List<TimelineDatasource.Annotation>> annotations() {
						return Collections.emptyMap();
					}
				};
			}

			@Override
			public String customHtmlView(Scale scale) {
				return customViewOf("Título", Map.of("Valor 1", 100));
			}

		};
	}

	private MagnitudeDefinition measurementOf(String name, String unit, String label) {
		return new MagnitudeDefinition().name(name).unit(unit).add("es", label);
	}

	private String customViewOf(String title, Map<String, Integer> variables) {
		return "<h4 style=\"padding:'0';margin:'0'\">" + title + "</h4>" + variables.entrySet().stream().map(v -> "<div style=\"fontWeight:'bold'\">" + v.getKey() + "</div><div>" + v.getValue() + "</div>").collect(Collectors.joining(""));
	}

}
