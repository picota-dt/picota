package io.picota.runtime;

import io.intino.alexandria.Scale;
import io.intino.alexandria.Timetag;
import io.intino.alexandria.event.message.MessageEvent;
import io.intino.alexandria.ingestion.EventSession;
import io.intino.alexandria.ingestion.SessionHandler;
import io.intino.alexandria.logger.Logger;
import io.intino.datahub.model.Sensor;
import io.intino.sumus.chronos.Magnitude;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;

import static io.intino.alexandria.event.Event.Format.Message;
import static java.util.Collections.addAll;

public class DataFeeder {
	private final RuntimeBox box;
	private final List<Exception> exceptions = new ArrayList<>();

	public DataFeeder(RuntimeBox box) {
		this.box = box;
	}

	public void feed(String entity, String contentType, InputStream stream) throws Exception {
		Sensor sensor = box.datahub().graph().sensorList(s -> s.name$().equals(entity)).findFirst().orElse(null);
		if (sensor == null) throw new Exception("Entity not found");
		SessionHandler handler = new SessionHandler(box.datahub().stageDirectory());
		EventSession session = handler.createEventSession();
		List<String> header = new ArrayList<>();
		String separator = separator(contentType);
		Iterator<String> lines = new BufferedReader(new InputStreamReader(stream)).lines().iterator();
		addAll(header, lines.next().split(separator));
		lines.forEachRemaining(l -> {
			try {
				String[] fields = l.split(separator);
				if (!exceptions.isEmpty()) return;
				List<String> magnitudes = header.subList(1, header.size());
				save(entity, magnitudes, ts(fields), map(sensor, values(fields), magnitudes), session);
			} catch (Exception e) {
				Logger.error(e);
				exceptions.add(e);
			}
		});
		session.close();
		if (!exceptions.isEmpty()) throw exceptions.getFirst();
	}

	private List<String> values(String[] fields) {
		return Arrays.asList(fields).subList(1, fields.length);
	}

	private static Instant ts(String[] fields) {
		return Instant.parse(fields[0]);
	}

	private static double[] map(Sensor sensor, List<String> values, List<String> magnitudes) throws Exception {
		double[] doubleValues = new double[values.size()];
		for (int i = 0; i < magnitudes.size(); i++) {
			String h = magnitudes.get(i);
			Sensor.Magnitude magnitude = sensor.magnitude(m -> m.name$().equals(h));
			if (magnitude == null) throw new Exception("Column not found: " + h);
			String type = magnitude.attribute(a -> a.name$().equalsIgnoreCase("Type")).value();
			if (type.equalsIgnoreCase("Enumerated")) {
				List<String> enums = List.of(magnitude.attribute(a -> a.name$().equalsIgnoreCase("Enumerated")).value().split(";"));
				doubleValues[i] = enums.indexOf(values.get(i));
			} else if (type.equalsIgnoreCase("Numeric")) doubleValues[i] = Double.parseDouble(values.get(i));
		}
		return doubleValues;
	}

	private void save(String entity, List<String> magnitudes, Instant ts, double[] values, EventSession session) {
		try {
			MessageEvent event = new MessageEvent(entity, entity);
			event.ts(ts).toMessage().set("values", values).set("magnitudes", magnitudes);
			session.put(entity, entity, new Timetag(ts, Scale.Day), Message, event);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private String separator(String contentType) {
		if (contentType.toLowerCase().contains("csv")) return ",";
		if (contentType.toLowerCase().contains("tsv")) return "\t";
		return ";";
	}
}