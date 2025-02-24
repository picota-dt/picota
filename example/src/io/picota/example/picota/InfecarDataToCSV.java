package io.picota.example.picota;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.intino.alexandria.Json;
import io.intino.alexandria.logger.Logger;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.*;

public class InfecarDataToCSV {
	public static final String Tank = "predictiveEnergyGeneration";
	public static final String SS = "predictiveEnergyGeneration";
	private final InputStream source;
	private final Map<String, String> magnitudes = new LinkedHashMap<>();
	private final File target;


	public InfecarDataToCSV(InputStream source, File target) {
		this.source = source;
		this.target = target;
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
		if (target.exists()) return;
		Logger.info("Importing infecar data...");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(source)); BufferedWriter writer = new BufferedWriter(new FileWriter(target))) {
			writer.write("ts," + magnitudes.values().stream().distinct().collect(joining(",")) + "\n");
			List<JsonObject> objects = filterAndGroupMeasures(reader);
			JsonObject current = null;
			for (JsonObject o : objects) current = mergeToHaveCompleteEvent(o, current, writer);
		}
	}

	private JsonObject mergeToHaveCompleteEvent(JsonObject o, JsonObject current, BufferedWriter writer) {
		if (current == null) {
			if (o.size() == 8) save(o, writer);
			else current = o;
		} else {
			if (o.size() == 8) {
				save(o, writer);
				current = null;
			} else {
				current = merge(tsFrom(o), List.of(current, o));
				if (current.size() == 8) {
					save(current, writer);
					current = null;
				}
			}
		}
		return current;
	}

	private List<JsonObject> filterAndGroupMeasures(BufferedReader reader) {
		return reader.lines().map(l -> Json.fromJson(l, JsonObject.class))
				.filter(InfecarDataToCSV::consFvMet)
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

	private void save(JsonObject measure, BufferedWriter writer) {
		try {
			Instant ts = tsFrom(measure);
			StringBuilder result = new StringBuilder();
			String measurements = valuesOf(measure)
					.mapToObj(d -> format(Locale.ENGLISH, "%.2f", d))
					.collect(joining(","));
			result.append(String.join(",", ts.toString(), measurements)).append("\n");
			writer.write(result.toString());
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private static Instant tsFrom(JsonObject measure) {
		return Instant.ofEpochSecond(measure.getAsJsonPrimitive("ts").getAsLong());
	}

	private DoubleStream valuesOf(JsonObject measure) {
		return magnitudes.values().stream().distinct().mapToDouble(m -> measure.getAsJsonPrimitive(m).getAsDouble());
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
		return extras.getAsDouble() / 10.;
	}
}
