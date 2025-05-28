package io.picota.digitaltwin.builder;

import com.google.gson.Gson;
import io.intino.alexandria.Scale;
import io.intino.alexandria.logger.Logger;
import io.picota.digitaltwin.TemporalColumns;
import io.quassar.DigitalTwin;
import io.quassar.DigitalTwin.DigitalSubject;
import io.quassar.DigitalTwin.DigitalSubject.InferenceModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.jetbrains.annotations.NotNull;
import systems.intino.datamarts.subjectstore.SubjectHistory;
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

import static io.picota.digitaltwin.utils.Utils.chronoUnitOf;
import static io.picota.digitaltwin.utils.Utils.periodOf;
import static java.time.temporal.ChronoUnit.HOURS;

public class DataPreparer {
	private final File dataDir;
	private final SubjectHistoryVault vault;
	private final Gson gson = new Gson();

	public DataPreparer(File temp, File dataDir) {
		this.vault = subjectStore(temp);
		this.dataDir = dataDir;
		this.dataDir.mkdirs();
	}

	void prepareData(DigitalSubject subject, InferenceModel inferenceModel, File dataset) throws IOException {
		SubjectHistory history = vault.open(subject.name$());
		if (dataset == null || dataset.length() == 0) {
			Logger.warn("No data found for " + subject.name$());
			return;
		}
		fillHistory(history, dataset);
		checkColumns(subject);
		ChronoUnit scale = chronoUnitOf(subject.resolution().scale());
		var timeHorizon = inferenceModel.isPrediction() ? inferenceModel.asPrediction().timeHorizon() : 0;
		var outName = timeHorizon == 0 ? outputVariableName(inferenceModel) : outputVariableName(inferenceModel) + "+" + timeHorizon;
		File file = new File(dataDir, history.name() + "_" + outputVariableName(inferenceModel) + ".tsv");
		SubjectHistoryView.of(history)
				.from(history.first().truncatedTo(scale))
				.to(history.last().plus(1, scale).truncatedTo(HOURS).minus(timeHorizon, scale))
				.period(period(subject.resolution()))
				.add(TemporalColumns.get())
				.add(inputVariables(history, inferenceModel))
				.add(outputVariable(inferenceModel))
				.export()
				.onlyCompleteRows()
				.to(new FileOutputStream(file));
		double[] means = history.tags().stream().mapToDouble(t -> history.query().number(t).all().summary().mean()).toArray();
		double[] stds = history.tags().stream().mapToDouble(t -> history.query().number(t).all().summary().sd()).toArray();
		Map<String, Object> normal = new HashMap<>(Map.of("means", means, "stds", stds));
		File jsonl = transformToJsonl(file, outName, normal, inferenceModel.asType().lookBack());
		transformToTsv(jsonl, outName);
		vault.close();
		file.delete();
	}

	private static String outputVariableName(InferenceModel inferenceModel) {
		if (inferenceModel.variable().isNumeric() && inferenceModel.variable().asNumeric().isLayered()) {
			String layers = inferenceModel.layers();
			if (layers == null) {
				Logger.warn("No layers found for " + inferenceModel.name$() + ". Logic not implemented yet");
				//TODO
				return inferenceModel.variable().name$();
			} else return inferenceModel.variable().name$() + "+" + layers;

		} else return inferenceModel.variable().name$();
	}

	private void transformToTsv(File jsonl, String outName) {


	}

	@NotNull
	private static List<ColumnDefinition> inputVariables(SubjectHistory history, InferenceModel inferenceModel) {
		boolean prediction = inferenceModel.isPrediction();
		return history.tags().stream()
				.filter(t -> prediction || !t.equals(outputVariableName(inferenceModel)))
				.map(DataPreparer::columnOf).toList();
	}

	private File transformToJsonl(File source, String outputVariable, Map<String, Object> headerValues, int lookback) throws IOException {
		File jsonl = new File(source.getParentFile(), source.getName().replace(".csv", ".jsonl"));
		String[] header = Files.lines(source.toPath()).findFirst().get().split("\t");
		headerValues.put("input_variables", Arrays.stream(header).filter(f -> !f.equals(outputVariable)).toArray(String[]::new));
		Set<String> features = Arrays.stream(header).filter(f -> !f.equals(outputVariable) && !f.contains("_sin") && !f.contains("_cos")).collect(Collectors.toSet());
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(jsonl))) {
			writer.write(gson.toJson(headerValues) + "\n");
			if (lookback > 0) writeWithLookBack(source, outputVariable, lookback, header, features, writer);
			else writeWithoutLookBack(source, outputVariable, header, features, writer);
		}
		return jsonl;
	}

	private void writeWithoutLookBack(File source, String outputVariable, String[] header, Set<String> features, BufferedWriter writer) throws IOException {
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.map(r -> inputDataOf(r, outputVariable, null, features))
				.map(i -> gson.toJson(i) + "\n")
				.forEach(c -> write(c, writer));
	}

	private void writeWithLookBack(File source, String outputVariable, int lookback, String[] header, Set<String> features, BufferedWriter writer) throws IOException {
		Queue<Map<String, Double>> queue = new CircularFifoQueue<>(lookback);
		Files.lines(source.toPath())
				.skip(1)
				.map(l -> rowOf(header, l.split("\t")))
				.peek(queue::add)
				.skip(lookback)
				.map(r -> inputDataOf(r, outputVariable, queue, features))
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

	private InputData inputDataOf(Map<String, Double> row, String outputVariable, Queue<Map<String, Double>> queue, Set<String> features) {
		return new InputData(row.get(outputVariable), features(row, features), time(row), queue == null ? new double[0] : lookBackFeatures(queue, features), queue == null ? new double[0] : lookBackTime(queue));
	}

	private double[] lookBackTime(Queue<Map<String, Double>> queue) {
		return queue.stream().flatMapToDouble(row -> Arrays.stream(time(row))).toArray();
	}

	private double[] lookBackFeatures(Queue<Map<String, Double>> queue, Set<String> features) {
		return queue.stream().flatMapToDouble(row -> Arrays.stream(features(row, features))).toArray();
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

	private record InputData(double out, double[] t_features, double[] t, double[] lookback_features,
							 double[] lookback_t) {
	}

	private void checkColumns(DigitalSubject subject) {
		List<String> variables = subject.inferenceModelList().stream().map(i -> outputVariableName(i)).toList();
		SubjectHistory history = vault.open(subject.name$());
		if (history.tags().isEmpty()) return;
		for (String variable : variables) {
			if (!history.tags().contains(variable)) {
				throw new IllegalStateException("Column " + variable + "not found in the dataset of" + subject.name$());
			}
		}
	}

	private static void fillHistory(SubjectHistory history, File dataset) throws IOException {
		String firstLine = Files.lines(dataset.toPath()).findFirst().get();
		String separator = firstLine.contains("\t") ? "\t" : ",";
		String[] header = firstLine.split(separator);
		SubjectHistory.Batch batch = history.batch();
		Files.lines(dataset.toPath()).skip(1).map(l -> l.split(separator)).forEach(line -> {
			SubjectHistory.Transaction t = batch.on(Instant.parse(line[0]), "");
			for (int i = 1; i < header.length; i++)
				if (!line[i].trim().isEmpty()) t.put(header[i].trim(), Double.parseDouble(line[i].trim()));
			t.terminate();
		});
		batch.terminate();
	}

	private static ColumnDefinition outputVariable(InferenceModel inference) {
		String name = outputVariableName(inference);
		String colName = name + (inference.isPrediction() ? "+" + inference.asPrediction().timeHorizon() : "");
		ColumnDefinition column = columnOf(colName, name);
		if (inference.isPrediction()) column.add(new LeadFilter(inference.asPrediction().timeHorizon()));
		column.add(new MinMaxNormalizationFilter());
		return column;
	}

	private static ColumnDefinition columnOf(String t) {
		return new ColumnDefinition(t, t + ".first");
	}

	private static ColumnDefinition columnOf(String name, String source) {
		return new ColumnDefinition(name, source + ".first");
	}

	private TemporalAmount period(DigitalTwin.DigitalSubject.Resolution resolution) {
		var scale = resolution.scale();
		return scale.ordinal() > Scale.Day.ordinal() ? Duration.of(resolution.amount(), chronoUnitOf(scale)) : periodOf(resolution.amount(), chronoUnitOf(scale));
	}

	private static SubjectHistoryVault subjectStore(File workspace) {
		return new SubjectHistoryVault("jdbc:sqlite:" + new File(workspace, "subjects.ddb"));
	}
}