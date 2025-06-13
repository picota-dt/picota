package io.picota.digitaltwin.control.utils;

import com.google.gson.*;
import io.quassar.picota.DigitalTwin;
import io.quassar.picota.DigitalTwin.DigitalSubject.Resolution.Scale;
import io.quassar.picota.Reality;
import io.quassar.picota.Variable;
import io.quassar.picota.Variable.Composite.Components;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.*;
import static java.util.stream.Collectors.toMap;

public class Utils {

	public static String digitalTwinId(URI uri) {
		Path path = Path.of(uri.getPath());
		return path.getName(path.getNameCount() - 1).toString().replace(".zip", "");
	}


	public static TemporalAmount periodOf(int amount, ChronoUnit unit) {
		return switch (unit) {
			case YEARS -> Period.ofYears(amount);
			case MONTHS -> Period.ofMonths(amount);
			case DAYS -> Period.ofDays(amount);
			case HOURS -> Period.ofDays(amount);
			case MINUTES -> Duration.ofMinutes(amount);
			default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
		};
	}

	public static ChronoUnit chronoUnitOf(Scale scale) {
		return switch (scale) {
			case Years -> YEARS;
			case Months -> MONTHS;
			case Days -> DAYS;
			case Hours -> HOURS;
			case Minutes -> MINUTES;
			case Seconds -> SECONDS;
		};
	}

	public static List<File> getFilesWithPrefix(File dir, String prefix) {
		List<File> result = new ArrayList<>();
		if (dir != null && dir.isDirectory()) {
			File[] files = dir.listFiles((d, name) -> name.startsWith(prefix));
			if (files != null) {
				result.addAll(Arrays.asList(files));
			}
		}
		return result;
	}

	public static Map<String, Variable> variableTypes(Reality.Subject subject) {
		Map<String, Variable> variables = subject.core$().ownerAs(Reality.class).variableList().stream()
				.flatMap(Utils::variableType)
				.collect(toMap(SimpleEntry::getKey, SimpleEntry::getValue));
		variables.putAll(subject.variableList().stream()
				.flatMap(Utils::variableType)
				.collect(toMap(SimpleEntry::getKey, SimpleEntry::getValue)));
		return variables;
	}

	@NotNull
	private static Stream<SimpleEntry<String, Variable>> variableType(Variable v) {
		return variableNamesOf(v).map(n -> new SimpleEntry<>(n, v));
	}

	public static List<String> variableNamesOf(Reality reality) {
		return reality.variableList().stream().flatMap(Utils::variableNamesOf).collect(Collectors.toList());
	}

	public static Stream<String> variableNamesOf(Reality.Subject s) {
		return s.variableList().stream().flatMap(Utils::variableNamesOf);
	}

	private static Stream<String> variableNamesOf(Variable var) {
		if (var.isComposite()) return var.asComposite().core$().findNode(Components.class).stream()
				.filter(c -> c.componentsList().isEmpty())
				.flatMap(c -> pathsOf(c).stream())
				.distinct()
				.map(c -> var.name$() + ":" + c);
		return Stream.of(var.name$());
	}

	private static List<String> pathsOf(Components c) {
		Components components = c.core$().ownerAs(Components.class);
		if (components == null) return c.values();
		else return c.values().stream().flatMap(cv -> combine(pathsOf(components), cv)).collect(Collectors.toList());
	}

	private static Stream<String> combine(List<String> container, String vl) {
		if (container.isEmpty()) return Stream.of(vl);
		return container.stream().map(c -> c + ":" + vl);
	}

	public static class InstantAdapter implements JsonDeserializer<Instant>, JsonSerializer<Instant> {
		@Override
		public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return Instant.parse(json.getAsString());
		}

		@Override
		public JsonElement serialize(Instant instant, Type type, JsonSerializationContext jsonSerializationContext) {
			return new JsonPrimitive(instant.toString());
		}
	}

	public static class FileAdapter implements JsonDeserializer<File>, JsonSerializer<File> {
		@Override
		public File deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return new File(json.getAsString());
		}

		@Override
		public JsonElement serialize(File file, Type type, JsonSerializationContext jsonSerializationContext) {
			return new JsonPrimitive(file.getAbsolutePath());
		}
	}

	public static int lookbackSize(DigitalTwin.DigitalSubject.InferenceModel i) {
		if (i.lookback() == null) return 0;
		return i.lookback().isWindow() ? i.lookback().asWindow().size() : 1;
	}

	public static double toDouble(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	public static ExecutorService createExecutor(String prefix) {
		return Executors.newSingleThreadExecutor(new ThreadFactory() {
			int counter = 0;

			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, prefix + counter++);
				thread.setDaemon(true);
				return thread;
			}
		});
	}
}
