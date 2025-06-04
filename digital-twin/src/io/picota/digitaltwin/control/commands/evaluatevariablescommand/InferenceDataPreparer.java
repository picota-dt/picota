package io.picota.digitaltwin.control.commands.evaluatevariablescommand;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.control.commands.trainvariablescommand.TemporalColumns;
import io.picota.digitaltwin.model.Archetype;
import io.quassar.picota.DigitalTwin.DigitalSubject;
import io.quassar.picota.DigitalTwin.DigitalSubject.InferenceModel;
import io.quassar.picota.DigitalTwin.DigitalSubject.Resolution;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import systems.intino.datamarts.subjectstore.SubjectHistory;
import systems.intino.datamarts.subjectstore.SubjectHistory.Transaction;
import systems.intino.datamarts.subjectstore.SubjectHistoryVault;
import systems.intino.datamarts.subjectstore.SubjectHistoryView;
import systems.intino.datamarts.subjectstore.calculator.model.filters.LeadFilter;
import systems.intino.datamarts.subjectstore.calculator.model.filters.MinMaxNormalizationFilter;
import systems.intino.datamarts.subjectstore.view.history.format.ColumnDefinition;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.picota.digitaltwin.control.utils.Utils.chronoUnitOf;
import static io.picota.digitaltwin.control.utils.Utils.periodOf;

public class InferenceDataPreparer {
	public static final String TSV = ".tsv";
	public static final String JSONL = ".jsonl";
	public static final String LAYER_SEPARATOR = ":";
	private final Archetype archetype;
	private final File dataDir;
	private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

	public InferenceDataPreparer(Archetype archetype) {
		this.archetype = archetype;
		this.dataDir = archetype.dataDirectory();
	}

	public void prepareData(DigitalSubject subject, InferenceModel inferenceModel, Map<String, Object> record) throws IOException {
		try (SubjectHistoryVault vault = subjectVault()) {
			SubjectHistory history = vault.open(subject.subject().name$());
			fillHistory(history, record, inferenceModel.asType().lookBack());
			String[] outputVariables = outputVariables(inferenceModel);
			checkColumns(history, subject.subject().name$(), outputVariables);
			for (String outputVariable : outputVariables) {
				var timeHorizon = inferenceModel.isPrediction() ? inferenceModel.asPrediction().timeHorizon() : 0;
				var outName = timeHorizon == 0 ? outputVariable : outputVariable + "+" + timeHorizon;
				File tsv = new File(dataDir, history.name() + "_" + outputVariable + TSV);
				createInitialTsv(subject.resolution(), inferenceModel, outputVariable, history, tsv);
				String[] header = Files.lines(tsv.toPath()).findFirst().get().split("\t");
				JsonObject metadata = getMetadata(subject.subject().name$(), outName);
				transformToJsonl(tsv, outName, header, metadata, inferenceModel.asType().lookBack());
				tsv.delete();
			}
		}
	}

	public JsonObject getMetadata(String subject, String variable) throws IOException {
		return gson.fromJson(Files.readString(archetype.metadataFile(subject, variable).toPath()), JsonObject.class);
	}

	private void createInitialTsv(Resolution resolution, InferenceModel inferenceModel, String outputVariable, SubjectHistory history, File file) throws IOException {
		ChronoUnit scale = chronoUnitOf(resolution.scale());
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale).minus(1, scale))
				.to(history.last().plus(1, scale).truncatedTo(scale))
				.period(period(resolution))
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel, outputVariable))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(file));
	}

	public static String[] outputVariables(InferenceModel inferenceModel) {
		if (inferenceModel.variable().isLayered()) {
			List<String> layers = inferenceModel.layers();
			if (layers == null || layers.isEmpty())
				layers = inferenceModel.variable().asLayered().layerList().stream().flatMap(l -> l.values().stream()).toList();
			return layers.stream().map(l -> inferenceModel.variable().name$() + LAYER_SEPARATOR + l).toArray(String[]::new);
		}
		return new String[]{inferenceModel.variable().name$()};
	}

	private static List<ColumnDefinition> inputVariables(SubjectHistory history, InferenceModel inferenceModel, String outputVariable) {
		boolean prediction = inferenceModel.isPrediction();
		return history.tags().stream()
				.filter(t -> prediction || !t.equals(outputVariable))
				.map(t1 -> new ColumnDefinition(t1, t1 + ".first")).toList();
	}

	private File transformToJsonl(File source, String outputVariable, String[] header, JsonObject metadata,
								  int lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(TSV, JSONL));
		Set<String> features = Arrays.stream(header).filter(f -> !f.equals(outputVariable) && !f.contains("_sin") && !f.contains("_cos")).collect(Collectors.toSet());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl))) {
			writer.write(gson.toJson(metadata) + "\n");
			if (lookback > 0) writeWithLookBack(source, lookback, header, features, writer);
			else writeWithoutLookBack(source, header, features, writer);
		}
		return jsonl;
	}

	private void writeWithoutLookBack(File source, String[] header, Set<String> features, BufferedWriter writer) throws IOException {
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, null, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private void writeWithLookBack(File source, int lookback, String[]
			header, Set<String> features, BufferedWriter writer) throws IOException {
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookback);
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.peek(queue::add)
				.skip(lookback)
				.map(r -> inputDataOf(r, queue, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private static void write(String c, BufferedWriter writer) {
		try {
			writer.write(c);
		} catch (IOException e) {
			Logger.error(e);
		}
	}

	private InputData inputDataOf(Map<String, Double> row, Queue<Map<String, Double>> queue, Set<String> features) {
		return new InputData(Double.NaN, features(row, features), time(row), queue == null ? new double[0][0] : lookBackFeatures(queue, features), queue == null ? new double[0][0] : lookBackTime(queue));
	}

	private double[][] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().map(this::time).toArray(double[][]::new);
	}

	private double[][] lookBackFeatures(Queue<Map<String, Double>> queue, Set<String> features) {
		return queue.stream().map(row -> features(row, features)).toArray(double[][]::new);
	}

	private double[] time(Map<String, Double> row) {
		return TemporalColumns.get().stream().map(c -> c.name).mapToDouble(row::get).toArray();
	}

	private static double[] features(Map<String, Double> row, Set<String> features) {
		return row.entrySet().stream().filter(e -> features.contains(e.getKey())).mapToDouble(Entry::getValue).toArray();
	}

	private Map<String, Double> rowOf(String[] header, String[] values) {
		return IntStream.range(0, header.length).boxed()
				.collect(Collectors.toMap(i -> header[i], i -> Double.parseDouble(values[i]), (a, b) -> b));
	}

	private record InputData(double out, double[] t_features, double[] t, double[][] lookback_features,
							 double[][] lookback_t) {
	}

	private void checkColumns(SubjectHistory history, String subject, String[] outputVariables) {
		if (history.tags().isEmpty()) return;
		for (String variable : outputVariables)
			if (!history.tags().contains(variable))
				throw new IllegalStateException("Column " + variable + " not found in the dataset of " + subject);
	}

	private static void fillHistory(SubjectHistory history, Map<String, Object> dataset, int lookback) throws IOException {
		SubjectHistory.Batch batch = history.batch();
		addTValues(dataset, batch);
		addLookback(dataset, lookback, batch);
		batch.terminate();
	}

	private static void addTValues(Map<String, Object> dataset, SubjectHistory.Batch batch) {
		Transaction t = batch.on(Instant.parse(dataset.get("instant").toString()), "");
		for (String key : dataset.keySet())
			if (!key.startsWith("instant") && !isLookback(key))
				t.put(key.trim(), valueOf(dataset.get(key)));
		t.terminate();
	}

	private static Number valueOf(Object value) {
		return value instanceof Number n ? n : Double.parseDouble(value.toString().trim());
	}

	private static void addLookback(Map<String, Object> dataset, int lookback, SubjectHistory.Batch batch) {
		IntStream.range(1, lookback).forEach(l -> {
			if (dataset.containsKey("instant-" + l)) {
				Transaction tLookback = batch.on(Instant.parse(dataset.get("instant-" + l).toString()), "");
				for (String key : dataset.keySet()) {
					if (!key.startsWith("instant") && lookback(key) == l)
						tLookback.put(key.trim(), valueOf(dataset.get(key)));
				}
				tLookback.terminate();
			}
		});
	}

	private static ColumnDefinition outputVariable(InferenceModel inference, String name) {
		String colName = name + (inference.isPrediction() ? "+" + inference.asPrediction().timeHorizon() : "");
		ColumnDefinition column = new ColumnDefinition(colName, name + ".first");
		if (inference.isPrediction()) column.add(new LeadFilter(inference.asPrediction().timeHorizon()));
		column.add(new MinMaxNormalizationFilter());
		return column;
	}

	private TemporalAmount period(Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.amount(), chronoUnitOf(scale)) : periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectVault() {
		return new SubjectHistoryVault("jdbc:sqlite:memory");
	}

	private static boolean isLookback(String key) {
		try {
			String substring = key.substring(key.length() - 2);
			return Integer.parseInt(substring) < 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static int lookback(String key) {
		try {
			String substring = key.substring(key.length() - 2);
			return Math.abs(Integer.parseInt(substring));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}