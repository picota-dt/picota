package io.picota.example.picota;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.intino.alexandria.Json;
import io.intino.alexandria.Scale;
import io.intino.alexandria.Timetag;
import io.intino.alexandria.datalake.file.FileDatalake;
import io.intino.alexandria.event.Event;
import io.intino.alexandria.event.message.MessageEvent;
import io.intino.alexandria.ingestion.EventSession;
import io.intino.alexandria.ingestion.SessionHandler;
import io.intino.alexandria.logger.Logger;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class InfecarDataPreparer {
	public static final String Tank = "Infecar-Twin";
	public static final String SS = "Infecar-Twin";
	private final FileDatalake datalake;
	private final File stageDir;
	private final InputStream source;
	private final Map<String, String> magnitudes = new LinkedHashMap<>();


	public InfecarDataPreparer(FileDatalake datalake, File stageDir, InputStream source) {
		this.datalake = datalake;
		this.stageDir = stageDir;
		this.source = source;
		magnitudes.put("temp_cell", "operational_cellTemperature");
		magnitudes.put("temp_envi", "weather_temperature");
		magnitudes.put("radiation", "weather_radiation");
		magnitudes.put("FV$reactive", "generation_reactivePower");
		magnitudes.put("FV2$reactive", "generation_reactivePower");
		magnitudes.put("FV$active", "generation_activePower");
		magnitudes.put("FV2$active", "generation_activePower");
		magnitudes.put("CONS$reactive", "consumption_reactivePower");
		magnitudes.put("CONS$active", "consumption_activePower");
	}

	public void run() throws IOException {
		if (datalake.measurementStore().tank(Tank).content().iterator().hasNext()) return;
		Logger.info("Importing infecar data...");
		SessionHandler handler = new SessionHandler(stageDir);
		EventSession session = handler.createEventSession();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source))) {
			List<JsonObject> objects = filterAndGroupMeasures(reader);
			JsonObject current = null;
			for (JsonObject o : objects) current = mergeToHaveCompleteEvent(o, current, session);
		}
		session.close();
	}

	private JsonObject mergeToHaveCompleteEvent(JsonObject o, JsonObject current, EventSession session) {
		if (current == null) {
			if (o.size() == 8) save(o, session);
			else current = o;
		} else {
			if (o.size() == 8) {
				save(o, session);
				current = null;
			} else {
				current = merge(tsFrom(o), List.of(current, o));
				if (current.size() == 8) {
					save(current, session);
					current = null;
				}
			}
		}
		return current;
	}

	private List<JsonObject> filterAndGroupMeasures(BufferedReader reader) {
		return reader.lines().map(l -> Json.fromJson(l, JsonObject.class))
				.filter(InfecarDataPreparer::consFvMet)
				.map(this::extractInfo)
				.collect(groupingBy(this::tsOf, LinkedHashMap::new, toList())).entrySet().stream()
				.map(e -> merge(e.getKey(), e.getValue()))
				.toList();
	}

	private static boolean consFvMet(JsonObject o) {
		String alias = alias(o);
		return "CONS".equals(alias) || alias.startsWith("FV") || "MET".equals(alias);
	}

	private JsonObject merge(Instant ts, List<JsonObject> values) {
		JsonObject result = new JsonObject();
		result.addProperty("ts", ts.getEpochSecond());
		magnitudes.values().stream()
				.filter(m -> contains(values, m))
				.forEach(m -> result.addProperty(m, values.stream().mapToDouble(o -> o.has(m) ? o.getAsJsonPrimitive(m).getAsDouble() : 0).average().orElse(0)));
		return result;
	}

	private boolean contains(List<JsonObject> values, String m) {
		return values.stream().anyMatch(o -> o.has(m));
	}

	private JsonObject extractInfo(JsonObject o) {
		JsonObject result = new JsonObject();
		result.addProperty("ts", tsOf(o.get("header").getAsJsonObject()).getEpochSecond());
		if (o.has("extras"))
			Stream.of("temp_cell", "temp_envi", "radiation").forEach(f -> result.addProperty(magnitudes.get(f), meteoValue(o, f)));
		else {
			String alias = alias(o);
			if (alias.startsWith("FV") || "CONS".equals(alias)) {
				Stream.of("reactive", "active").forEach(field -> {
					double value = o.get("power").getAsJsonObject().get("active").getAsJsonArray().asList().stream()
										   .filter(e -> !e.getAsString().startsWith("%"))
										   .mapToDouble(JsonElement::getAsDouble).sum() / 1000;
					result.addProperty(magnitudes.get(alias + "$" + field), value);
				});
			}
		}
		return result;
	}

	private void save(JsonObject measure, EventSession session) {
		try {
			MessageEvent event = new MessageEvent(Tank, SS);
			Instant ts = tsFrom(measure);
			double[] measurements = valuesOf(measure);
			event.ts(ts).toMessage().set("values", measurements).set("magnitudes", magnitudes.values());
			session.put(Tank, SS, new Timetag(ts, Scale.Day), Event.Format.Message, event);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static Instant tsFrom(JsonObject measure) {
		return Instant.ofEpochSecond(measure.getAsJsonPrimitive("ts").getAsLong());
	}

	private double[] valuesOf(JsonObject measure) {
		return magnitudes.values().stream().distinct().mapToDouble(m -> measure.getAsJsonPrimitive(m).getAsDouble()).toArray();
	}

	private static String alias(JsonObject o2) {
		return o2.get("header").getAsJsonObject().get("alias").getAsString();
	}

	private Instant tsOf(JsonObject o) {
		long ts = o.get("ts").getAsLong();
		return Instant.from(Instant.ofEpochSecond(ts)).truncatedTo(ChronoUnit.MINUTES);
	}

	private static double meteoValue(JsonObject o, String f) {
		JsonElement extras = o.get("extras").getAsJsonObject().get(f);
		if (extras.getAsString().startsWith("%")) return 0;
		return extras.getAsDouble();
	}
}
